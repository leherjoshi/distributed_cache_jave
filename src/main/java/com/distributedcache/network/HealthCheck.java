package com.distributedcache.network;

import java.io.Serializable;

/**
 * Message for checking the health status of a node.
 */
public class HealthCheck extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public HealthCheck(String sourceNodeId) {
        super(MessageType.HEALTH_CHECK, sourceNodeId);
    }
}
