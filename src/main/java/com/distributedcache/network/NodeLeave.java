package com.distributedcache.network;

import java.io.Serializable;

/**
 * Message broadcast when a node leaves the cluster.
 */
public class NodeLeave extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String leavingNodeId;
    
    public NodeLeave(String sourceNodeId, String leavingNodeId) {
        super(MessageType.NODE_LEAVE, sourceNodeId);
        this.leavingNodeId = leavingNodeId;
    }
    
    public String getLeavingNodeId() {
        return leavingNodeId;
    }
}
