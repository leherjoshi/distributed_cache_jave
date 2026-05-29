package com.distributedcache.network;

import java.io.Serializable;

/**
 * Message for requesting a value from the cache.
 */
public class GetRequest extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String key;
    
    public GetRequest(String sourceNodeId, String key) {
        super(MessageType.GET_REQUEST, sourceNodeId);
        this.key = key;
    }
    
    public String getKey() {
        return key;
    }
}
