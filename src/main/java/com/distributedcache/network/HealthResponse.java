package com.distributedcache.network;

import com.distributedcache.node.HealthStatus;
import java.io.Serializable;

/**
 * Message containing the response to a health check.
 */
public class HealthResponse extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final HealthStatus status;
    private final long memoryUsageBytes;
    private final long capacityBytes;
    
    public HealthResponse(String sourceNodeId, HealthStatus status, long memoryUsageBytes, long capacityBytes) {
        super(MessageType.HEALTH_RESPONSE, sourceNodeId);
        this.status = status;
        this.memoryUsageBytes = memoryUsageBytes;
        this.capacityBytes = capacityBytes;
    }
    
    public HealthStatus getStatus() {
        return status;
    }
    
    public long getMemoryUsageBytes() {
        return memoryUsageBytes;
    }
    
    public long getCapacityBytes() {
        return capacityBytes;
    }
    
    public double getMemoryUsagePercentage() {
        return capacityBytes > 0 ? (double) memoryUsageBytes / capacityBytes : 0.0;
    }
}
