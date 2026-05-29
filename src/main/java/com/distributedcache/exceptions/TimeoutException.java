package com.distributedcache.exceptions;

/**
 * Exception thrown when a network operation times out.
 * This is a network error and is retryable.
 */
public class TimeoutException extends NetworkException {
    
    public TimeoutException(String message) {
        super(message);
    }
    
    public TimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
