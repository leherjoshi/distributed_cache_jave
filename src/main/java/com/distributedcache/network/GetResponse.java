package com.distributedcache.network;

import java.io.Serializable;
import java.util.Optional;

/**
 * Message containing the response to a get request.
 */
public class GetResponse extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String key;
    private final Object value;
    private final boolean found;
    
    public GetResponse(String sourceNodeId, String key, Object value) {
        super(MessageType.GET_RESPONSE, sourceNodeId);
        this.key = key;
        this.value = value;
        this.found = value != null;
    }
    
    public String getKey() {
        return key;
    }
    
    public Optional<Object> getValue() {
        return found ? Optional.of(value) : Optional.empty();
    }
    
    public boolean isFound() {
        return found;
    }
}
