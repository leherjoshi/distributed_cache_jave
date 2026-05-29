package com.distributedcache.network;

import java.io.Serializable;

/**
 * Message for storing a key-value pair in the cache.
 */
public class PutRequest extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String key;
    private final Object value;
    
    public PutRequest(String sourceNodeId, String key, Object value) {
        super(MessageType.PUT_REQUEST, sourceNodeId);
        this.key = key;
        this.value = value;
    }
    
    public String getKey() {
        return key;
    }
    
    public Object getValue() {
        return value;
    }
}
