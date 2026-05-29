package com.distributedcache.exceptions;

/**
 * Exception thrown when a cache value is invalid (e.g., exceeds size limit).
 * This is a client error and is not retryable.
 */
public class InvalidValueException extends CacheException {
    
    public InvalidValueException(String message) {
        super(message, ErrorCode.INVALID_VALUE, false);
    }
    
    public InvalidValueException(String message, Throwable cause) {
        super(message, cause, ErrorCode.INVALID_VALUE, false);
    }
}
