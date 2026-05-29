package com.distributedcache.exceptions;

/**
 * Error codes for cache exceptions.
 */
public enum ErrorCode {
    INVALID_KEY,
    INVALID_VALUE,
    INVALID_CONFIGURATION,
    NETWORK_ERROR,
    TIMEOUT,
    SERIALIZATION_ERROR,
    NODE_UNAVAILABLE,
    REPLICATION_ERROR,
    UNKNOWN_ERROR
}
