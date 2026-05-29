package com.distributedcache.exceptions;

/**
 * Exception thrown when a network error occurs.
 * This is a network error and is retryable.
 */
public class NetworkException extends CacheException {
    
    public NetworkException(String message) {
        super(message, ErrorCode.NETWORK_ERROR, true);
    }
    
    public NetworkException(String message, Throwable cause) {
        super(message, cause, ErrorCode.NETWORK_ERROR, true);
    }
}
