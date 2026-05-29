package com.distributedcache.exceptions;

/**
 * Base exception for all cache-related errors.
 */
public class CacheException extends Exception {
    private final ErrorCode errorCode;
    private final boolean retryable;
    
    public CacheException(String message, ErrorCode errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public CacheException(String message, Throwable cause, ErrorCode errorCode, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
}
