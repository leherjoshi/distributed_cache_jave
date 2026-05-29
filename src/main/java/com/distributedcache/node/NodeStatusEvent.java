package com.distributedcache.node;

import com.distributedcache.hashing.NodeInfo;
import java.time.Instant;

/**
 * Event representing a change in node health status.
 */
public class NodeStatusEvent {
    private final NodeInfo node;
    private final HealthStatus oldStatus;
    private final HealthStatus newStatus;
    private final Instant timestamp;
    
    public NodeStatusEvent(NodeInfo node, HealthStatus oldStatus, HealthStatus newStatus) {
        this.node = node;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.timestamp = Instant.now();
    }
    
    public NodeInfo getNode() {
        return node;
    }
    
    public HealthStatus getOldStatus() {
        return oldStatus;
    }
    
    public HealthStatus getNewStatus() {
        return newStatus;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "NodeStatusEvent{" +
                "node=" + node +
                ", oldStatus=" + oldStatus +
                ", newStatus=" + newStatus +
                ", timestamp=" + timestamp +
                '}';
    }
}
