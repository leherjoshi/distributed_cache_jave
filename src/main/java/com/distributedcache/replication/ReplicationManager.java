package com.distributedcache.replication;

import com.distributedcache.hashing.NodeInfo;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manages replication of cache entries across nodes.
 */
public interface ReplicationManager {
    
    /**
     * Maximum replication factor supported.
     */
    int MAX_REPLICATION_FACTOR = 5;
    
    /**
     * Replication timeout in milliseconds.
     */
    long REPLICATION_TIMEOUT_MS = 100;
    
    /**
     * Replicates a put operation to replica nodes.
     * 
     * @param key the cache key
     * @param value the value to replicate
     * @param replicas the list of replica nodes (excluding primary)
     * @return future that completes when replication finishes
     */
    <V> CompletableFuture<Void> replicatePut(String key, V value, List<NodeInfo> replicas);
    
    /**
     * Replicates a delete operation to replica nodes.
     */
    CompletableFuture<Void> replicateDelete(String key, List<NodeInfo> replicas);
    
    /**
     * Synchronizes data with a new replica node.
     */
    CompletableFuture<Void> syncWithReplica(NodeInfo replica, List<String> keys);
    
    /**
     * Gets the configured replication factor.
     */
    int getReplicationFactor();
}
