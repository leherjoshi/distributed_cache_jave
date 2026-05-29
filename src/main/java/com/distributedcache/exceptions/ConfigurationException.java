package com.distributedcache.exceptions;

/**
 * Exception thrown when configuration is invalid.
 * This is a client error and is not retryable.
 */
public class ConfigurationException extends CacheException {
    
    public ConfigurationException(String message) {
        super(message, ErrorCode.INVALID_CONFIGURATION, false);
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause, ErrorCode.INVALID_CONFIGURATION, false);
    }
}
