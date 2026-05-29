package com.distributedcache.network;

import java.io.Serializable;

/**
 * Message containing the response to a put request.
 */
public class PutResponse extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String key;
    private final boolean success;
    
    public PutResponse(String sourceNodeId, String key, boolean success) {
        super(MessageType.PUT_RESPONSE, sourceNodeId);
        this.key = key;
        this.success = success;
    }
    
    public String getKey() {
        return key;
    }
    
    public boolean isSuccess() {
        return success;
    }
}
