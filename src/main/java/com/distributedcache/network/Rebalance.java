package com.distributedcache.network;

import java.io.Serializable;
import java.util.List;

/**
 * Message for triggering data rebalancing across nodes.
 */
public class Rebalance extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final List<String> affectedKeys;
    private final String targetNodeId;
    
    public Rebalance(String sourceNodeId, List<String> affectedKeys, String targetNodeId) {
        super(MessageType.REBALANCE, sourceNodeId);
        this.affectedKeys = affectedKeys;
        this.targetNodeId = targetNodeId;
    }
    
    public List<String> getAffectedKeys() {
        return affectedKeys;
    }
    
    public String getTargetNodeId() {
        return targetNodeId;
    }
}
