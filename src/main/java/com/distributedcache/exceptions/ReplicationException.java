package com.distributedcache.exceptions;

/**
 * Exception thrown when replication fails.
 * This is a node error and is retryable.
 */
public class ReplicationException extends CacheException {
    
    public ReplicationException(String message) {
        super(message, ErrorCode.REPLICATION_ERROR, true);
    }
    
    public ReplicationException(String message, Throwable cause) {
        super(message, cause, ErrorCode.REPLICATION_ERROR, true);
    }
}
