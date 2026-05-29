package com.distributedcache.exceptions;

/**
 * Exception thrown when serialization or deserialization fails.
 * This is a system error and is not retryable.
 */
public class SerializationException extends CacheException {
    
    public SerializationException(String message) {
        super(message, ErrorCode.SERIALIZATION_ERROR, false);
    }
    
    public SerializationException(String message, Throwable cause) {
        super(message, cause, ErrorCode.SERIALIZATION_ERROR, false);
    }
}
