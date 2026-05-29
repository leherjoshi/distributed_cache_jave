package com.distributedcache.eviction;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * First-In-First-Out (FIFO) eviction policy implementation.
 * Evicts the oldest entries based on creation time (insertion order).
 * 
 * <p>This implementation uses a LinkedHashMap to maintain insertion order
 * and track the size of each entry for efficient victim selection.</p>
 * 
 * <p>Thread-safe: Uses read-write locks to allow concurrent reads while
 * ensuring exclusive access during modifications.</p>
 */
public class FIFOEvictionPolicy<K, V> implements EvictionPolicy<K, V> {
    
    /**
     * Maintains insertion order and tracks entry sizes.
     * Key: cache key, Value: size in bytes
     */
    private final LinkedHashMap<K, Long> insertionOrder;
    
    /**
     * Lock for thread-safe access to the insertion order map.
     */
    private final ReadWriteLock lock;
    
    /**
     * Creates a new FIFO eviction policy.
     */
    public FIFOEvictionPolicy() {
        // LinkedHashMap with insertion-order (default)
        this.insertionOrder = new LinkedHashMap<>();
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>FIFO policy does not track access, so this is a no-op.</p>
     */
    @Override
    public void onAccess(K key) {
        // FIFO doesn't care about access patterns, only insertion order
        // No-op
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Records the entry in insertion order. If the key already exists,
     * it is removed and re-added to update its position to the end.</p>
     */
    @Override
    public void onPut(K key, V value) {
        if (key == null) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            // Estimate size - use a simple heuristic
            long size = estimateSize(value);
            
            // Remove if exists (to update position) and add to end
            insertionOrder.remove(key);
            insertionOrder.put(key, size);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Removes the entry from tracking.</p>
     */
    @Override
    public void onRemove(K key) {
        if (key == null) {
            return;
        }
        
        lock.writeLock().lock();
        try {
            insertionOrder.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * {@inheritDoc}
     * 
     * <p>Selects the oldest entries (first inserted) until the target
     * number of bytes is reached. Entries are selected in insertion order.</p>
     * 
     * @param targetBytes the number of bytes to free
     * @return set of keys to evict, in insertion order
     */
    @Override
    public Set<K> selectVictims(long targetBytes) {
        Set<K> victims = new LinkedHashSet<>();
        
        if (targetBytes <= 0) {
            return victims;
        }
        
        lock.readLock().lock();
        try {
            long freedBytes = 0;
            
            // Iterate in insertion order (oldest first)
            for (Map.Entry<K, Long> entry : insertionOrder.entrySet()) {
                if (freedBytes >= targetBytes) {
                    break;
                }
                
                victims.add(entry.getKey());
                freedBytes += entry.getValue();
            }
        } finally {
            lock.readLock().unlock();
        }
        
        return victims;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public EvictionPolicyType getType() {
        return EvictionPolicyType.FIFO;
    }
    
    /**
     * Estimates the size of a value in bytes.
     * 
     * <p>This is a simple heuristic that provides a reasonable estimate
     * for common types. For more accurate sizing, consider using a
     * serialization-based approach.</p>
     * 
     * @param value the value to estimate
     * @return estimated size in bytes
     */
    private long estimateSize(V value) {
        if (value == null) {
            return 0;
        }
        
        // Simple heuristic for common types
        if (value instanceof String) {
            // Each char is 2 bytes in Java (UTF-16) + object overhead
            return ((String) value).length() * 2L + 40;
        } else if (value instanceof Integer || value instanceof Float) {
            return 16; // 4 bytes + object overhead
        } else if (value instanceof Long || value instanceof Double) {
            return 24; // 8 bytes + object overhead
        } else if (value instanceof byte[]) {
            return ((byte[]) value).length + 16;
        } else {
            // Default estimate for unknown types
            return 100;
        }
    }
    
    /**
     * Returns the number of entries currently tracked.
     * Useful for testing and debugging.
     * 
     * @return number of tracked entries
     */
    int size() {
        lock.readLock().lock();
        try {
            return insertionOrder.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
