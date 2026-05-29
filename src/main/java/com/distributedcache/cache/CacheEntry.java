package com.distributedcache.cache;

import java.time.Instant;
import java.io.Serializable;

/**
 * Represents a cache entry with metadata for eviction policies.
 */
public class CacheEntry<V> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String key;
    private final V value;
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;
    private volatile long accessCount;
    private final long sizeBytes;
    
    public CacheEntry(String key, V value, long sizeBytes) {
        this.key = key;
        this.value = value;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
        this.lastAccessedAt = createdAt;
        this.accessCount = 0;
    }
    
    public void recordAccess() {
        this.lastAccessedAt = Instant.now();
        this.accessCount++;
    }
    
    public String getKey() {
        return key;
    }
    
    public V getValue() {
        return value;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }
    
    public long getAccessCount() {
        return accessCount;
    }
    
    public long getSizeBytes() {
        return sizeBytes;
    }
}
