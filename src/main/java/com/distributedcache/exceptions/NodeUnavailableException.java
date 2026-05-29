package com.distributedcache.exceptions;

/**
 * Exception thrown when a cache node is unavailable.
 * This is a node error and is retryable with failover.
 */
public class NodeUnavailableException extends CacheException {
    
    public NodeUnavailableException(String message) {
        super(message, ErrorCode.NODE_UNAVAILABLE, true);
    }
    
    public NodeUnavailableException(String message, Throwable cause) {
        super(message, cause, ErrorCode.NODE_UNAVAILABLE, true);
    }
}
