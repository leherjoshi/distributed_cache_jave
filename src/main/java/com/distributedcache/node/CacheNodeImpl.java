package com.distributedcache.node;

import com.distributedcache.cache.LocalCache;
import com.distributedcache.exceptions.CacheException;
import com.distributedcache.exceptions.ErrorCode;
import com.distributedcache.exceptions.InvalidKeyException;
import com.distributedcache.exceptions.InvalidValueException;
import com.distributedcache.exceptions.NetworkException;
import com.distributedcache.hashing.HashRing;
import com.distributedcache.hashing.NodeAddress;
import com.distributedcache.hashing.NodeInfo;
import com.distributedcache.network.*;
import com.distributedcache.replication.ReplicationManager;
import com.distributedcache.utils.CacheConfiguration;
import com.distributedcache.utils.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of CacheNode that integrates all distributed cache components.
 * This is the main entry point for a cache node in the cluster.
 */
public class CacheNodeImpl implements CacheNode {
    private static final Logger logger = LoggerFactory.getLogger(CacheNodeImpl.class);
    
    // Rebalancing delay in seconds (requirement 10.4)
    private static final int REBALANCE_DELAY_SECONDS = 30;
    
    // Node list persistence file
    private static final String NODE_LIST_FILE = "cache-nodes.dat";
    
    private final String nodeId;
    private final NodeAddress address;
    private final LocalCache<String, Object> localCache;
    private final HashRing hashRing;
    private final ReplicationManager replicationManager;
    private final HealthMonitor healthMonitor;
    private final NetworkServer networkServer;
    private final NetworkClient networkClient;
    private final MetricsCollector metricsCollector;
    private final ConfigurationManager configurationManager;
    private final CacheConfiguration configuration;
    
    // Known nodes list (requirement 10.3)
    private final Set<NodeInfo> knownNodes = ConcurrentHashMap.newKeySet();
    private final ReadWriteLock knownNodesLock = new ReentrantReadWriteLock();
    
    // Rebalancing scheduler
    private final ScheduledExecutorService rebalanceScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingRebalance = null;
    
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    
    /**
     * Constructs a new CacheNode with all required components.
     *
     * @param nodeId unique identifier for this node
     * @param address network address for this node
     * @param localCache local cache storage
     * @param hashRing consistent hash ring for key distribution
     * @param replicationManager manages data replication
     * @param healthMonitor monitors node health
     * @param networkServer handles incoming messages
     * @param networkClient sends outgoing messages
     * @param metricsCollector collects performance metrics
     * @param configurationManager manages configuration
     * @param configuration cache configuration
     */
    public CacheNodeImpl(String nodeId, NodeAddress address,
                        LocalCache<String, Object> localCache,
                        HashRing hashRing,
                        ReplicationManager replicationManager,
                        HealthMonitor healthMonitor,
                        NetworkServer networkServer,
                        NetworkClient networkClient,
                        MetricsCollector metricsCollector,
                        ConfigurationManager configurationManager,
                        CacheConfiguration configuration) {
        this.nodeId = nodeId;
        this.address = address;
        this.localCache = localCache;
        this.hashRing = hashRing;
        this.replicationManager = replicationManager;
        this.healthMonitor = healthMonitor;
        this.networkServer = networkServer;
        this.networkClient = networkClient;
        this.metricsCollector = metricsCollector;
        this.configurationManager = configurationManager;
        this.configuration = configuration;
    }
    
    @Override
    public void start() throws CacheException {
        if (!started.compareAndSet(false, true)) {
            logger.warn("Node {} already started", nodeId);
            return;
        }
        
        logger.info("Starting cache node {} at {}", nodeId, address);
        
        try {
            // Load persisted node list (requirement 10.5)
            loadNodeList();
            logger.info("Loaded {} known nodes from disk", knownNodes.size());
            
            // Start network server
            networkServer.start();
            logger.info("Network server started on port {}", networkServer.getPort());
            
            // Register message handlers for inter-node communication
            registerMessageHandlers();
            logger.info("Message handlers registered");
            
            // Add this node to the hash ring
            NodeInfo thisNode = new NodeInfo(nodeId, address.getHost(), address.getPort());
            hashRing.addNode(thisNode);
            addKnownNode(thisNode);
            logger.info("Node added to hash ring");
            
            // Re-add previously known nodes to hash ring
            knownNodesLock.readLock().lock();
            try {
                for (NodeInfo node : knownNodes) {
                    if (!node.getNodeId().equals(nodeId)) {
                        hashRing.addNode(node);
                        logger.info("Re-added known node {} to hash ring", node.getNodeId());
                    }
                }
            } finally {
                knownNodesLock.readLock().unlock();
            }
            
            // Start health monitoring
            healthMonitor.start();
            logger.info("Health monitor started");
            
            // Register health status change callback
            healthMonitor.onStatusChange(event -> {
                logger.info("Node status changed: {}", event);
                // Could trigger rebalancing or other actions here
            });
            
            // Broadcast node join to cluster (requirement 10.1)
            broadcastNodeJoin(thisNode);
            logger.info("Node join broadcast sent");
            
            logger.info("Cache node {} successfully started", nodeId);
            
        } catch (NetworkException e) {
            started.set(false);
            logger.error("Failed to start cache node {}", nodeId, e);
            throw new CacheException("Failed to start cache node: " + e.getMessage(), e, 
                ErrorCode.NETWORK_ERROR, true);
        } catch (Exception e) {
            started.set(false);
            logger.error("Unexpected error starting cache node {}", nodeId, e);
            throw new CacheException("Unexpected error starting cache node: " + e.getMessage(), e, 
                ErrorCode.UNKNOWN_ERROR, false);
        }
    }
    
    @Override
    public void stop() throws CacheException {
        if (!stopped.compareAndSet(false, true)) {
            logger.warn("Node {} already stopped", nodeId);
            return;
        }
        
        logger.info("Stopping cache node {}", nodeId);
        
        try {
            // Cancel any pending rebalance
            if (pendingRebalance != null && !pendingRebalance.isDone()) {
                pendingRebalance.cancel(false);
                logger.info("Cancelled pending rebalance");
            }
            
            // Shutdown rebalance scheduler
            rebalanceScheduler.shutdown();
            try {
                if (!rebalanceScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    rebalanceScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                rebalanceScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            // Broadcast node leave to cluster
            broadcastNodeLeave();
            logger.info("Node leave broadcast sent");
            
            // Stop health monitoring
            healthMonitor.stop();
            logger.info("Health monitor stopped");
            
            // Remove this node from hash ring
            hashRing.removeNode(nodeId);
            logger.info("Node removed from hash ring");
            
            // Persist node list before stopping
            saveNodeList();
            logger.info("Node list persisted to disk");
            
            // Stop network server
            networkServer.stop();
            logger.info("Network server stopped");
            
            // Clear local cache
            localCache.clear();
            logger.info("Local cache cleared");
            
            logger.info("Cache node {} successfully stopped", nodeId);
            
        } catch (NetworkException e) {
            logger.error("Error stopping cache node {}", nodeId, e);
            throw new CacheException("Error stopping cache node: " + e.getMessage(), e, 
                ErrorCode.NETWORK_ERROR, false);
        } catch (Exception e) {
            logger.error("Unexpected error stopping cache node {}", nodeId, e);
            throw new CacheException("Unexpected error stopping cache node: " + e.getMessage(), e, 
                ErrorCode.UNKNOWN_ERROR, false);
        }
    }
    
    @Override
    public String getNodeId() {
        return nodeId;
    }
    
    @Override
    public NodeAddress getAddress() {
        return address;
    }
    
    @Override
    public <V> Optional<V> handleGet(String key) {
        long startTime = System.currentTimeMillis();
        
        try {
            Optional<Object> result = localCache.get(key);
            
            // Update metrics
            if (result.isPresent()) {
                metricsCollector.recordHit();
            } else {
                metricsCollector.recordMiss();
            }
            
            long latency = System.currentTimeMillis() - startTime;
            metricsCollector.recordGetLatency(latency);
            
            // Update memory usage metrics
            metricsCollector.recordMemoryUsage(
                localCache.getMemoryUsage(),
                localCache.getCapacity()
            );
            
            logger.debug("GET {} - found: {}, latency: {}ms", key, result.isPresent(), latency);
            
            @SuppressWarnings("unchecked")
            Optional<V> typedResult = (Optional<V>) result;
            return typedResult;
            
        } catch (Exception e) {
            logger.error("Error handling GET for key {}", key, e);
            metricsCollector.recordMiss();
            return Optional.empty();
        }
    }
    
    @Override
    public <V> void handlePut(String key, V value) {
        try {
            // Store locally
            localCache.put(key, value);
            logger.debug("PUT {} - stored locally", key);
            
            // Update memory usage metrics
            metricsCollector.recordMemoryUsage(
                localCache.getMemoryUsage(),
                localCache.getCapacity()
            );
            
            // Replicate to other nodes
            replicateAsync(key, value);
            
        } catch (InvalidKeyException | InvalidValueException e) {
            logger.error("Invalid key or value for PUT operation: key={}", key, e);
            throw new RuntimeException("Invalid key or value: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error handling PUT for key {}", key, e);
            throw new RuntimeException("Error handling PUT: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void handleDelete(String key) {
        try {
            // Remove locally
            localCache.remove(key);
            logger.debug("DELETE {} - removed locally", key);
            
            // Update memory usage metrics
            metricsCollector.recordMemoryUsage(
                localCache.getMemoryUsage(),
                localCache.getCapacity()
            );
            
            // Replicate delete to other nodes
            replicateDeleteAsync(key);
            
        } catch (Exception e) {
            logger.error("Error handling DELETE for key {}", key, e);
            throw new RuntimeException("Error handling DELETE: " + e.getMessage(), e);
        }
    }
    
    @Override
    public NodeMetrics getMetrics() {
        CacheMetrics cacheMetrics = metricsCollector.getMetrics();
        return new NodeMetrics(
            cacheMetrics.getHitsPerSecond(),
            cacheMetrics.getMissesPerSecond(),
            cacheMetrics.getAverageGetLatencyMs(),
            cacheMetrics.getMemoryUsagePercentage(),
            cacheMetrics.getTotalHits(),
            cacheMetrics.getTotalMisses()
        );
    }
    
    @Override
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
    
    @Override
    public HealthStatus getHealthStatus() {
        return healthMonitor.getNodeStatus(nodeId);
    }
    
    @Override
    public void close() throws Exception {
        if (!stopped.get()) {
            stop();
        }
    }
    
    /**
     * Registers message handlers for inter-node communication.
     */
    private void registerMessageHandlers() {
        // Handle GET requests
        networkServer.registerHandler(MessageType.GET_REQUEST, message -> {
            GetRequest request = (GetRequest) message;
            Optional<Object> value = handleGet(request.getKey());
            return new GetResponse(nodeId, request.getKey(), value.orElse(null));
        });
        
        // Handle PUT requests
        networkServer.registerHandler(MessageType.PUT_REQUEST, message -> {
            PutRequest request = (PutRequest) message;
            handlePut(request.getKey(), request.getValue());
            return new PutResponse(nodeId, request.getKey(), true);
        });
        
        // Handle DELETE requests
        networkServer.registerHandler(MessageType.DELETE_REQUEST, message -> {
            DeleteRequest request = (DeleteRequest) message;
            handleDelete(request.getKey());
            return new DeleteResponse(nodeId, request.getKey(), true);
        });
        
        // Handle REPLICATE requests (from primary to replica)
        networkServer.registerHandler(MessageType.REPLICATE_REQUEST, message -> {
            ReplicateRequest request = (ReplicateRequest) message;
            if (request.isDelete()) {
                handleDelete(request.getKey());
            } else {
                handlePut(request.getKey(), request.getValue());
            }
            return null; // No response needed for replication
        });
        
        // Handle NODE_JOIN messages (requirement 10.2)
        networkServer.registerHandler(MessageType.NODE_JOIN, message -> {
            NodeJoin joinMessage = (NodeJoin) message;
            NodeInfo joiningNode = joinMessage.getNodeInfo();
            
            // Don't add ourselves
            if (!joiningNode.getNodeId().equals(nodeId)) {
                hashRing.addNode(joiningNode);
                addKnownNode(joiningNode);
                logger.info("Node {} joined the cluster", joiningNode.getNodeId());
                
                // Schedule rebalancing (requirement 10.4)
                scheduleRebalance();
            }
            
            return null; // No response needed
        });
        
        // Handle NODE_LEAVE messages
        networkServer.registerHandler(MessageType.NODE_LEAVE, message -> {
            NodeLeave leaveMessage = (NodeLeave) message;
            String leavingNodeId = leaveMessage.getLeavingNodeId();
            
            if (!leavingNodeId.equals(nodeId)) {
                hashRing.removeNode(leavingNodeId);
                removeKnownNode(leavingNodeId);
                logger.info("Node {} left the cluster", leavingNodeId);
                
                // Schedule rebalancing (requirement 10.4)
                scheduleRebalance();
            }
            
            return null; // No response needed
        });
        
        // Handle HEALTH_CHECK messages
        networkServer.registerHandler(MessageType.HEALTH_CHECK, message -> {
            HealthCheck healthCheck = (HealthCheck) message;
            HealthStatus status = getHealthStatus();
            long memoryUsage = localCache.getMemoryUsage();
            long capacity = localCache.getCapacity();
            return new HealthResponse(nodeId, status, memoryUsage, capacity);
        });
    }
    
    /**
     * Broadcasts node join message to the cluster.
     */
    private void broadcastNodeJoin(NodeInfo nodeInfo) {
        try {
            NodeJoin joinMessage = new NodeJoin(nodeId, nodeInfo);
            networkClient.broadcast(joinMessage)
                .exceptionally(throwable -> {
                    logger.warn("Failed to broadcast node join", throwable);
                    return null;
                });
        } catch (Exception e) {
            logger.warn("Error broadcasting node join", e);
        }
    }
    
    /**
     * Broadcasts node leave message to the cluster.
     */
    private void broadcastNodeLeave() {
        try {
            NodeLeave leaveMessage = new NodeLeave(nodeId, nodeId);
            networkClient.broadcast(leaveMessage)
                .exceptionally(throwable -> {
                    logger.warn("Failed to broadcast node leave", throwable);
                    return null;
                });
        } catch (Exception e) {
            logger.warn("Error broadcasting node leave", e);
        }
    }
    
    /**
     * Asynchronously replicates a put operation to replica nodes.
     */
    private <V> void replicateAsync(String key, V value) {
        CompletableFuture.runAsync(() -> {
            try {
                // Get replica nodes (excluding this primary node)
                List<NodeInfo> replicaNodes = hashRing.getReplicaNodes(
                    key, 
                    configuration.getReplicationFactor()
                );
                
                // Remove this node from the list (we're the primary)
                replicaNodes.removeIf(node -> node.getNodeId().equals(nodeId));
                
                if (!replicaNodes.isEmpty()) {
                    replicationManager.replicatePut(key, value, replicaNodes)
                        .exceptionally(throwable -> {
                            logger.warn("Replication failed for key {}", key, throwable);
                            return null;
                        });
                }
            } catch (Exception e) {
                logger.error("Error initiating replication for key {}", key, e);
            }
        });
    }
    
    /**
     * Asynchronously replicates a delete operation to replica nodes.
     */
    private void replicateDeleteAsync(String key) {
        CompletableFuture.runAsync(() -> {
            try {
                // Get replica nodes (excluding this primary node)
                List<NodeInfo> replicaNodes = hashRing.getReplicaNodes(
                    key, 
                    configuration.getReplicationFactor()
                );
                
                // Remove this node from the list (we're the primary)
                replicaNodes.removeIf(node -> node.getNodeId().equals(nodeId));
                
                if (!replicaNodes.isEmpty()) {
                    replicationManager.replicateDelete(key, replicaNodes)
                        .exceptionally(throwable -> {
                            logger.warn("Delete replication failed for key {}", key, throwable);
                            return null;
                        });
                }
            } catch (Exception e) {
                logger.error("Error initiating delete replication for key {}", key, e);
            }
        });
    }
    
    /**
     * Adds a node to the known nodes list and persists it.
     * Requirement 10.3: Maintain list of known nodes in memory
     * Requirement 10.5: Persist node list to disk
     */
    private void addKnownNode(NodeInfo node) {
        knownNodesLock.writeLock().lock();
        try {
            if (knownNodes.add(node)) {
                logger.debug("Added node {} to known nodes list", node.getNodeId());
                saveNodeList();
            }
        } finally {
            knownNodesLock.writeLock().unlock();
        }
    }
    
    /**
     * Removes a node from the known nodes list and persists the change.
     * Requirement 10.3: Maintain list of known nodes in memory
     * Requirement 10.5: Persist node list to disk
     */
    private void removeKnownNode(String nodeId) {
        knownNodesLock.writeLock().lock();
        try {
            boolean removed = knownNodes.removeIf(node -> node.getNodeId().equals(nodeId));
            if (removed) {
                logger.debug("Removed node {} from known nodes list", nodeId);
                saveNodeList();
            }
        } finally {
            knownNodesLock.writeLock().unlock();
        }
    }
    
    /**
     * Loads the persisted node list from disk.
     * Requirement 10.5: Persist node list to survive restarts
     */
    private void loadNodeList() {
        Path nodeListPath = Paths.get(NODE_LIST_FILE);
        
        if (!Files.exists(nodeListPath)) {
            logger.info("No persisted node list found at {}", nodeListPath);
            return;
        }
        
        knownNodesLock.writeLock().lock();
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(nodeListPath.toFile()))) {
            
            @SuppressWarnings("unchecked")
            Set<NodeInfo> loadedNodes = (Set<NodeInfo>) ois.readObject();
            knownNodes.clear();
            knownNodes.addAll(loadedNodes);
            
            logger.info("Loaded {} nodes from {}", knownNodes.size(), nodeListPath);
            
        } catch (FileNotFoundException e) {
            logger.info("Node list file not found: {}", nodeListPath);
        } catch (IOException | ClassNotFoundException e) {
            logger.warn("Failed to load node list from {}", nodeListPath, e);
        } finally {
            knownNodesLock.writeLock().unlock();
        }
    }
    
    /**
     * Persists the current node list to disk.
     * Requirement 10.5: Persist node list to survive restarts
     */
    private void saveNodeList() {
        Path nodeListPath = Paths.get(NODE_LIST_FILE);
        
        knownNodesLock.readLock().lock();
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(nodeListPath.toFile()))) {
            
            // Create a copy to avoid holding the lock during I/O
            Set<NodeInfo> nodesCopy = new HashSet<>(knownNodes);
            oos.writeObject(nodesCopy);
            
            logger.debug("Saved {} nodes to {}", nodesCopy.size(), nodeListPath);
            
        } catch (IOException e) {
            logger.error("Failed to save node list to {}", nodeListPath, e);
        } finally {
            knownNodesLock.readLock().unlock();
        }
    }
    
    /**
     * Schedules a rebalancing operation to occur within 30 seconds.
     * Requirement 10.4: Trigger rebalancing within 30 seconds of topology change
     */
    private void scheduleRebalance() {
        // Cancel any pending rebalance
        if (pendingRebalance != null && !pendingRebalance.isDone()) {
            pendingRebalance.cancel(false);
            logger.debug("Cancelled previous pending rebalance");
        }
        
        // Schedule new rebalance
        pendingRebalance = rebalanceScheduler.schedule(
            this::performRebalance,
            REBALANCE_DELAY_SECONDS,
            TimeUnit.SECONDS
        );
        
        logger.info("Scheduled rebalancing to occur in {} seconds", REBALANCE_DELAY_SECONDS);
    }
    
    /**
     * Performs the actual rebalancing operation.
     * This method identifies keys that need to be moved and transfers them to the correct nodes.
     * Requirement 10.4: Rebalance cache entries when nodes join/leave
     */
    private void performRebalance() {
        logger.info("Starting rebalancing operation");
        
        try {
            // Get all keys from local cache
            Set<String> localKeys = getAllLocalKeys();
            
            if (localKeys.isEmpty()) {
                logger.info("No keys to rebalance");
                return;
            }
            
            int movedCount = 0;
            int keptCount = 0;
            
            // Check each key to see if it still belongs to this node
            for (String key : localKeys) {
                NodeInfo primaryNode = hashRing.getPrimaryNode(key);
                
                if (primaryNode == null) {
                    logger.warn("No primary node found for key {}", key);
                    continue;
                }
                
                // If this key no longer belongs to this node, move it
                if (!primaryNode.getNodeId().equals(nodeId)) {
                    Optional<Object> value = localCache.get(key);
                    
                    if (value.isPresent()) {
                        // Send the key-value pair to the correct node
                        transferKeyToNode(key, value.get(), primaryNode);
                        
                        // Remove from local cache after successful transfer
                        localCache.remove(key);
                        movedCount++;
                        
                        logger.debug("Moved key {} to node {}", key, primaryNode.getNodeId());
                    }
                } else {
                    keptCount++;
                }
            }
            
            logger.info("Rebalancing completed: {} keys moved, {} keys kept", movedCount, keptCount);
            
        } catch (Exception e) {
            logger.error("Error during rebalancing", e);
        }
    }
    
    /**
     * Gets all keys from the local cache.
     * This is a helper method for rebalancing.
     */
    private Set<String> getAllLocalKeys() {
        return localCache.keySet();
    }
    
    /**
     * Transfers a key-value pair to another node.
     * This is a helper method for rebalancing.
     */
    private void transferKeyToNode(String key, Object value, NodeInfo targetNode) {
        try {
            PutRequest putRequest = new PutRequest(nodeId, key, value);
            
            networkClient.sendWithRetry(targetNode, putRequest)
                .thenAccept(response -> {
                    logger.debug("Successfully transferred key {} to node {}", 
                        key, targetNode.getNodeId());
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to transfer key {} to node {}", 
                        key, targetNode.getNodeId(), throwable);
                    return null;
                });
                
        } catch (Exception e) {
            logger.error("Error transferring key {} to node {}", 
                key, targetNode.getNodeId(), e);
        }
    }
}
