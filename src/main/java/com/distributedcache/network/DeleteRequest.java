package com.distributedcache.network;

import java.io.Serializable;

/**
 * Message for deleting a key from the cache.
 */
public class DeleteRequest extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String key;
    
    public DeleteRequest(String sourceNodeId, String key) {
        super(MessageType.DELETE_REQUEST, sourceNodeId);
        this.key = key;
    }
    
    public String getKey() {
        return key;
    }
}
