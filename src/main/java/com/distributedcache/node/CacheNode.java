package com.distributedcache.node;

import com.distributedcache.exceptions.CacheException;
import com.distributedcache.hashing.NodeAddress;
import java.util.Optional;

/**
 * Represents a single node in the distributed cache cluster.
 * Handles local storage, replication, and inter-node communication.
 */
public interface CacheNode extends AutoCloseable {
    
    /**
     * Starts the cache node and joins the cluster.
     */
    void start() throws CacheException;
    
    /**
     * Stops the cache node and leaves the cluster gracefully.
     */
    void stop() throws CacheException;
    
    /**
     * Gets the unique identifier for this node.
     */
    String getNodeId();
    
    /**
     * Gets the network address of this node.
     */
    NodeAddress getAddress();
    
    /**
     * Handles a get request (local or forwarded).
     */
    <V> Optional<V> handleGet(String key);
    
    /**
     * Handles a put request (local or forwarded).
     */
    <V> void handlePut(String key, V value);
    
    /**
     * Handles a delete request (local or forwarded).
     */
    void handleDelete(String key);
    
    /**
     * Returns current node metrics.
     */
    NodeMetrics getMetrics();
    
    /**
     * Returns the metrics collector for this node.
     */
    MetricsCollector getMetricsCollector();
    
    /**
     * Returns current node health status.
     */
    HealthStatus getHealthStatus();
}
