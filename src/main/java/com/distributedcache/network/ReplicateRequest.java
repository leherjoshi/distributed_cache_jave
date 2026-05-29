package com.distributedcache.network;

import java.io.Serializable;

/**
 * Message for replicating a cache entry to a replica node.
 */
public class ReplicateRequest extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String key;
    private final Object value;
    private final boolean isDelete;
    
    public ReplicateRequest(String sourceNodeId, String key, Object value) {
        super(MessageType.REPLICATE_REQUEST, sourceNodeId);
        this.key = key;
        this.value = value;
        this.isDelete = false;
    }
    
    public ReplicateRequest(String sourceNodeId, String key) {
        super(MessageType.REPLICATE_REQUEST, sourceNodeId);
        this.key = key;
        this.value = null;
        this.isDelete = true;
    }
    
    public String getKey() {
        return key;
    }
    
    public Object getValue() {
        return value;
    }
    
    public boolean isDelete() {
        return isDelete;
    }
}
