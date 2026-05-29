package com.distributedcache.node;

import com.distributedcache.hashing.NodeInfo;
import java.time.Duration;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Monitors health of cache nodes in the cluster.
 */
public interface HealthMonitor {
    
    /**
     * Health check interval.
     */
    Duration DEFAULT_CHECK_INTERVAL = Duration.ofSeconds(5);
    
    /**
     * Number of consecutive failures before marking unavailable.
     */
    int FAILURE_THRESHOLD = 3;
    
    /**
     * Starts health monitoring.
     */
    void start();
    
    /**
     * Stops health monitoring.
     */
    void stop();
    
    /**
     * Registers a callback for node status changes.
     */
    void onStatusChange(Consumer<NodeStatusEvent> callback);
    
    /**
     * Gets the current status of a node.
     */
    HealthStatus getNodeStatus(String nodeId);
    
    /**
     * Gets all healthy nodes.
     */
    Set<NodeInfo> getHealthyNodes();
    
    /**
     * Manually triggers a health check for a node.
     */
    void checkNode(String nodeId);
}
