package com.distributedcache.exceptions;

/**
 * Exception thrown when a cache key is invalid (e.g., exceeds size limit).
 * This is a client error and is not retryable.
 */
public class InvalidKeyException extends CacheException {
    
    public InvalidKeyException(String message) {
        super(message, ErrorCode.INVALID_KEY, false);
    }
    
    public InvalidKeyException(String message, Throwable cause) {
        super(message, cause, ErrorCode.INVALID_KEY, false);
    }
}
