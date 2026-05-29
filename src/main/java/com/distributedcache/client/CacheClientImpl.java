package com.distributedcache.client;

import com.distributedcache.exceptions.CacheException;
import com.distributedcache.exceptions.ErrorCode;
import com.distributedcache.exceptions.NetworkException;
import com.distributedcache.hashing.HashRing;
import com.distributedcache.hashing.NodeInfo;
import com.distributedcache.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of CacheClient that handles routing, retries, and failover.
 * Routes requests to the correct nodes based on consistent hashing and
 * automatically fails over to replica nodes when primary is unavailable.
 */
public class CacheClientImpl implements CacheClient {
    private static final Logger logger = LoggerFactory.getLogger(CacheClientImpl.class);
    
    // Retry timeout in milliseconds (requirement 6.5)
    private static final long RETRY_TIMEOUT_MS = 5000;  // 5 seconds
    
    private final NetworkClient networkClient;
    private final HashRing hashRing;
    private final int replicationFactor;
    
    /**
     * Creates a new CacheClient.
     *
     * @param networkClient the network client for communication
     * @param hashRing the hash ring for routing decisions
     * @param replicationFactor the number of replicas per key
     */
    public CacheClientImpl(NetworkClient networkClient, HashRing hashRing, int replicationFactor) {
        if (networkClient == null) {
            throw new IllegalArgumentException("NetworkClient cannot be null");
        }
        if (hashRing == null) {
            throw new IllegalArgumentException("HashRing cannot be null");
        }
        if (replicationFactor < 1 || replicationFactor > 5) {
            throw new IllegalArgumentException("Replication factor must be between 1 and 5");
        }
        
        this.networkClient = networkClient;
        this.hashRing = hashRing;
        this.replicationFactor = replicationFactor;
        
        logger.info("CacheClient initialized with replication factor {}", replicationFactor);
    }
    
    @Override
    public <V> Optional<V> get(String key) throws CacheException {
        validateKey(key);
        
        try {
            CompletableFuture<Optional<V>> future = getAsync(key);
            return future.get(RETRY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("GET operation failed for key {}", key, e);
            throw new CacheException("GET operation failed: " + e.getMessage(), e,
                ErrorCode.NETWORK_ERROR, true);
        }
    }
    
    @Override
    public <V> void put(String key, V value) throws CacheException {
        validateKey(key);
        
        try {
            putAsync(key, value).get(RETRY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("PUT operation failed for key {}", key, e);
            throw new CacheException("PUT operation failed: " + e.getMessage(), e,
                ErrorCode.NETWORK_ERROR, true);
        }
    }
    
    @Override
    public void delete(String key) throws CacheException {
        validateKey(key);
        
        try {
            deleteAsync(key).get(RETRY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("DELETE operation failed for key {}", key, e);
            throw new CacheException("DELETE operation failed: " + e.getMessage(), e,
                ErrorCode.NETWORK_ERROR, true);
        }
    }
    
    @Override
    public <V> Map<String, V> batchGet(List<String> keys) throws CacheException {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // Validate all keys
        for (String key : keys) {
            validateKey(key);
        }
        
        // Group keys by target node (requirement 6.6)
        Map<NodeInfo, List<String>> keysByNode = new HashMap<>();
        
        for (String key : keys) {
            NodeInfo primaryNode = hashRing.getPrimaryNode(key);
            keysByNode.computeIfAbsent(primaryNode, k -> new ArrayList<>()).add(key);
        }
        
        // Send batch requests to each node
        Map<String, V> results = new HashMap<>();
        
        for (Map.Entry<NodeInfo, List<String>> entry : keysByNode.entrySet()) {
            NodeInfo node = entry.getKey();
            List<String> nodeKeys = entry.getValue();
            
            try {
                // Send individual GET requests for each key to this node
                for (String key : nodeKeys) {
                    Optional<V> value = getFromNode(key, node);
                    value.ifPresent(v -> results.put(key, v));
                }
            } catch (Exception e) {
                logger.warn("Batch GET failed for node {}", node.getNodeId(), e);
                // Continue with other nodes
            }
        }
        
        logger.debug("Batch GET completed: {} keys requested, {} values found", 
            keys.size(), results.size());
        
        return results;
    }
    
    @Override
    public <V> CompletableFuture<Optional<V>> getAsync(String key) {
        validateKey(key);
        
        // Get primary node (requirement 6.4)
        NodeInfo primaryNode = hashRing.getPrimaryNode(key);
        
        // Try primary node first
        CompletableFuture<Optional<V>> primaryFuture = this.<V>getFromNodeAsync(key, primaryNode);
        
        return primaryFuture.exceptionally(throwable -> {
            logger.warn("GET from primary node {} failed for key {}, trying replicas", 
                primaryNode.getNodeId(), key);
            
            // Failover to replicas (requirement 6.5)
            return this.<V>tryReplicas(key);
        });
    }
    
    @Override
    public <V> CompletableFuture<Void> putAsync(String key, V value) {
        validateKey(key);
        
        // Get primary node (requirement 6.4)
        NodeInfo primaryNode = hashRing.getPrimaryNode(key);
        
        // Send PUT request to primary node
        PutRequest request = new PutRequest("client", key, value);
        
        return networkClient.sendWithRetry(primaryNode, request)
            .thenApply(response -> {
                if (response instanceof PutResponse) {
                    PutResponse putResponse = (PutResponse) response;
                    if (putResponse.isSuccess()) {
                        logger.debug("PUT successful for key {} on node {}", 
                            key, primaryNode.getNodeId());
                        return (Void) null;
                    } else {
                        throw new RuntimeException("PUT failed on primary node");
                    }
                } else {
                    throw new RuntimeException("Unexpected response type: " + response.getClass());
                }
            })
            .exceptionally(throwable -> {
                logger.error("PUT failed for key {} on primary node {}", 
                    key, primaryNode.getNodeId(), throwable);
                throw new RuntimeException("PUT operation failed", throwable);
            });
    }
    
    @Override
    public CompletableFuture<Void> deleteAsync(String key) {
        validateKey(key);
        
        // Get primary node (requirement 6.4)
        NodeInfo primaryNode = hashRing.getPrimaryNode(key);
        
        // Send DELETE request to primary node
        DeleteRequest request = new DeleteRequest("client", key);
        
        return networkClient.sendWithRetry(primaryNode, request)
            .thenApply(response -> {
                if (response instanceof DeleteResponse) {
                    DeleteResponse deleteResponse = (DeleteResponse) response;
                    if (deleteResponse.isSuccess()) {
                        logger.debug("DELETE successful for key {} on node {}", 
                            key, primaryNode.getNodeId());
                        return (Void) null;
                    } else {
                        throw new RuntimeException("DELETE failed on primary node");
                    }
                } else {
                    throw new RuntimeException("Unexpected response type: " + response.getClass());
                }
            })
            .exceptionally(throwable -> {
                logger.error("DELETE failed for key {} on primary node {}", 
                    key, primaryNode.getNodeId(), throwable);
                throw new RuntimeException("DELETE operation failed", throwable);
            });
    }
    
    @Override
    public void close() throws Exception {
        logger.info("CacheClient closed");
        // NetworkClient is shared and managed externally, so we don't close it here
    }
    
    /**
     * Attempts to get a value from a specific node.
     */
    private <V> Optional<V> getFromNode(String key, NodeInfo node) {
        try {
            CompletableFuture<Optional<V>> future = this.<V>getFromNodeAsync(key, node);
            return future.get(RETRY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warn("Failed to get key {} from node {}", key, node.getNodeId(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Asynchronously attempts to get a value from a specific node.
     */
    private <V> CompletableFuture<Optional<V>> getFromNodeAsync(String key, NodeInfo node) {
        GetRequest request = new GetRequest("client", key);
        
        return networkClient.sendWithRetry(node, request)
            .thenApply(response -> {
                if (response instanceof GetResponse) {
                    GetResponse getResponse = (GetResponse) response;
                    Optional<Object> valueOpt = getResponse.getValue();
                    
                    if (valueOpt.isPresent()) {
                        @SuppressWarnings("unchecked")
                        V typedValue = (V) valueOpt.get();
                        logger.debug("GET successful for key {} from node {}", 
                            key, node.getNodeId());
                        return Optional.of(typedValue);
                    } else {
                        logger.debug("Key {} not found on node {}", key, node.getNodeId());
                        return Optional.empty();
                    }
                } else {
                    throw new RuntimeException("Unexpected response type: " + response.getClass());
                }
            });
    }
    
    /**
     * Tries to get a value from replica nodes when primary fails.
     * Requirement 6.5: Retry with failover to replica nodes within 50ms
     */
    private <V> Optional<V> tryReplicas(String key) {
        // Get replica nodes
        List<NodeInfo> replicaNodes = hashRing.getReplicaNodes(key, replicationFactor);
        
        if (replicaNodes.isEmpty()) {
            logger.error("No replica nodes available for key {}", key);
            return Optional.empty();
        }
        
        // Try each replica in order
        for (NodeInfo replica : replicaNodes) {
            try {
                Optional<V> result = getFromNode(key, replica);
                if (result.isPresent()) {
                    logger.info("Successfully retrieved key {} from replica node {}", 
                        key, replica.getNodeId());
                    return result;
                }
            } catch (Exception e) {
                logger.warn("Failed to get key {} from replica node {}", 
                    key, replica.getNodeId(), e);
                // Continue to next replica
            }
        }
        
        logger.error("All replicas failed for key {}", key);
        return Optional.empty();
    }
    
    /**
     * Validates a cache key.
     * Requirement 1.5: Keys must be non-null and max 256 bytes
     */
    private void validateKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (key.getBytes().length > 256) {
            throw new IllegalArgumentException("Key exceeds maximum size of 256 bytes");
        }
    }
}
