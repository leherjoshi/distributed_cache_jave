package com.distributedcache.eviction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Least Frequently Used (LFU) eviction policy implementation.
 * Tracks access frequency for each key and evicts entries with the lowest access count.
 * Thread-safe implementation using concurrent data structures and read-write locks.
 */
public class LFUEvictionPolicy<K, V> implements EvictionPolicy<K, V> {
    
    // Thread-safe map to track access frequency for each key
    private final ConcurrentHashMap<K, Long> accessFrequency;
    
    // Thread-safe map to track size of each entry
    private final ConcurrentHashMap<K, Long> entrySizes;
    
    // Read-write lock for protecting selectVictims operation
    private final ReadWriteLock lock;
    
    /**
     * Creates a new LFU eviction policy.
     */
    public LFUEvictionPolicy() {
        this.accessFrequency = new ConcurrentHashMap<>();
        this.entrySizes = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Called when an entry is accessed (get operation).
     * Increments the access count for the key.
     * 
     * @param key the key that was accessed
     */
    @Override
    public void onAccess(K key) {
        if (key == null) {
            return;
        }
        // Atomically increment the access count
        accessFrequency.compute(key, (k, count) -> (count == null) ? 1L : count + 1);
    }
    
    /**
     * Called when an entry is added or updated.
     * Initializes or updates tracking for the key.
     * 
     * @param key the key that was added/updated
     * @param value the value (used to estimate size if needed)
     */
    @Override
    public void onPut(K key, V value) {
        if (key == null) {
            return;
        }
        // Initialize access count if not present (don't reset if already exists)
        accessFrequency.putIfAbsent(key, 0L);
        
        // Estimate and store entry size
        long size = estimateSize(key, value);
        entrySizes.put(key, size);
    }
    
    /**
     * Called when an entry is removed.
     * Cleans up tracking data for the key.
     * 
     * @param key the key that was removed
     */
    @Override
    public void onRemove(K key) {
        if (key == null) {
            return;
        }
        accessFrequency.remove(key);
        entrySizes.remove(key);
    }
    
    /**
     * Selects entries to evict to free the target number of bytes.
     * Selects entries with the lowest access frequency first.
     * 
     * @param targetBytes number of bytes to free
     * @return set of keys to evict
     */
    @Override
    public Set<K> selectVictims(long targetBytes) {
        Set<K> victims = new LinkedHashSet<>();
        
        if (targetBytes <= 0) {
            return victims;
        }
        
        lock.readLock().lock();
        try {
            // Create a list of entries sorted by access frequency (ascending)
            List<Map.Entry<K, Long>> sortedEntries = new ArrayList<>(accessFrequency.entrySet());
            sortedEntries.sort(Map.Entry.comparingByValue());
            
            long freedBytes = 0;
            
            // Select entries with lowest frequency until we've freed enough space
            for (Map.Entry<K, Long> entry : sortedEntries) {
                if (freedBytes >= targetBytes) {
                    break;
                }
                
                K key = entry.getKey();
                Long size = entrySizes.get(key);
                
                if (size != null) {
                    victims.add(key);
                    freedBytes += size;
                }
            }
            
        } finally {
            lock.readLock().unlock();
        }
        
        return victims;
    }
    
    /**
     * Returns the eviction policy type.
     * 
     * @return LFU policy type
     */
    @Override
    public EvictionPolicyType getType() {
        return EvictionPolicyType.LFU;
    }
    
    /**
     * Estimates the size of a cache entry in bytes.
     * This is a simple estimation based on key and value characteristics.
     * 
     * @param key the cache key
     * @param value the cache value
     * @return estimated size in bytes
     */
    private long estimateSize(K key, V value) {
        long size = 0;
        
        // Estimate key size
        if (key instanceof String) {
            size += ((String) key).length() * 2; // 2 bytes per char in Java
        } else {
            size += 64; // Default estimate for object overhead
        }
        
        // Estimate value size
        if (value instanceof String) {
            size += ((String) value).length() * 2;
        } else if (value instanceof byte[]) {
            size += ((byte[]) value).length;
        } else {
            size += 128; // Default estimate for object
        }
        
        // Add overhead for entry metadata
        size += 64; // Approximate overhead for CacheEntry object
        
        return size;
    }
    
    /**
     * Gets the current access frequency for a key (for testing purposes).
     * 
     * @param key the key to query
     * @return the access frequency, or 0 if not tracked
     */
    long getAccessFrequency(K key) {
        return accessFrequency.getOrDefault(key, 0L);
    }
    
    /**
     * Gets the number of entries currently tracked.
     * 
     * @return the number of tracked entries
     */
    int size() {
        return accessFrequency.size();
    }
}
