package com.distributedcache.cache;

import com.distributedcache.eviction.EvictionPolicy;
import com.distributedcache.exceptions.InvalidKeyException;
import com.distributedcache.exceptions.InvalidValueException;
import com.distributedcache.utils.MessageSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe implementation of LocalCache using ConcurrentHashMap.
 * Supports configurable eviction policies and tracks memory usage.
 */
public class LocalCacheImpl<V> implements LocalCache<String, V> {
    
    // Constants for validation
    private static final int MAX_KEY_SIZE_BYTES = 256;
    private static final long MAX_VALUE_SIZE_BYTES = 1024 * 1024; // 1 MB
    private static final double EVICTION_THRESHOLD = 0.95;
    
    private final ConcurrentHashMap<String, CacheEntry<V>> storage;
    private final AtomicLong currentMemoryUsage;
    private final long capacity;
    private final MessageSerializer serializer;
    private final ReadWriteLock evictionLock;
    private volatile EvictionPolicy<String, V> evictionPolicy;
    
    /**
     * Creates a new LocalCacheImpl with the specified capacity and serializer.
     * 
     * @param capacity the maximum memory capacity in bytes
     * @param serializer the serializer for estimating object sizes
     */
    public LocalCacheImpl(long capacity, MessageSerializer serializer) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (serializer == null) {
            throw new IllegalArgumentException("Serializer cannot be null");
        }
        
        this.storage = new ConcurrentHashMap<>();
        this.currentMemoryUsage = new AtomicLong(0);
        this.capacity = capacity;
        this.serializer = serializer;
        this.evictionLock = new ReentrantReadWriteLock();
    }
    
    @Override
    public void put(String key, V value) throws InvalidKeyException, InvalidValueException {
        validateKey(key);
        validateValue(value);
        
        long keySize = estimateKeySize(key);
        long valueSize = serializer.estimateSize(value);
        long entrySize = keySize + valueSize;
        
        // Check if we're updating an existing entry
        CacheEntry<V> oldEntry = storage.get(key);
        long oldEntrySize = (oldEntry != null) ? oldEntry.getSizeBytes() : 0;
        
        // Calculate net size change
        long netSizeChange = entrySize - oldEntrySize;
        
        // Check if eviction is needed before putting
        if (netSizeChange > 0 && needsEviction(netSizeChange)) {
            performEviction(netSizeChange);
        }
        
        // Remove old entry if it exists to update memory usage correctly
        if (oldEntry != null) {
            currentMemoryUsage.addAndGet(-oldEntry.getSizeBytes());
            if (evictionPolicy != null) {
                evictionPolicy.onRemove(key);
            }
        }
        
        // Create and store new entry
        CacheEntry<V> newEntry = new CacheEntry<>(key, value, entrySize);
        storage.put(key, newEntry);
        currentMemoryUsage.addAndGet(entrySize);
        
        // Notify eviction policy
        if (evictionPolicy != null) {
            evictionPolicy.onPut(key, value);
        }
    }
    
    @Override
    public Optional<V> get(String key) {
        CacheEntry<V> entry = storage.get(key);
        if (entry != null) {
            entry.recordAccess();
            if (evictionPolicy != null) {
                evictionPolicy.onAccess(key);
            }
            return Optional.of(entry.getValue());
        }
        return Optional.empty();
    }
    
    @Override
    public void remove(String key) {
        CacheEntry<V> entry = storage.remove(key);
        if (entry != null) {
            currentMemoryUsage.addAndGet(-entry.getSizeBytes());
            if (evictionPolicy != null) {
                evictionPolicy.onRemove(key);
            }
        }
    }
    
    @Override
    public int size() {
        return storage.size();
    }
    
    @Override
    public long getMemoryUsage() {
        return currentMemoryUsage.get();
    }
    
    @Override
    public long getCapacity() {
        return capacity;
    }
    
    @Override
    public double getMemoryUsagePercentage() {
        return (double) currentMemoryUsage.get() / capacity;
    }
    
    @Override
    public void clear() {
        evictionLock.writeLock().lock();
        try {
            storage.clear();
            currentMemoryUsage.set(0);
        } finally {
            evictionLock.writeLock().unlock();
        }
    }
    
    @Override
    public void setEvictionPolicy(EvictionPolicy<String, V> policy) {
        this.evictionPolicy = policy;
    }
    
    @Override
    public Set<String> keySet() {
        // Return a snapshot of current keys
        return Set.copyOf(storage.keySet());
    }
    
    /**
     * Validates that the key meets size requirements.
     * 
     * @throws InvalidKeyException if the key is invalid
     */
    private void validateKey(String key) throws InvalidKeyException {
        if (key == null) {
            throw new InvalidKeyException("Key cannot be null");
        }
        
        int keySize = key.getBytes(StandardCharsets.UTF_8).length;
        if (keySize > MAX_KEY_SIZE_BYTES) {
            throw new InvalidKeyException(
                String.format("Key size (%d bytes) exceeds maximum allowed size (%d bytes)", 
                    keySize, MAX_KEY_SIZE_BYTES)
            );
        }
    }
    
    /**
     * Validates that the value meets size requirements.
     * 
     * @throws InvalidValueException if the value is invalid
     */
    private void validateValue(V value) throws InvalidValueException {
        if (value == null) {
            throw new InvalidValueException("Value cannot be null");
        }
        
        long valueSize = serializer.estimateSize(value);
        if (valueSize > MAX_VALUE_SIZE_BYTES) {
            throw new InvalidValueException(
                String.format("Value size (%d bytes) exceeds maximum allowed size (%d bytes)", 
                    valueSize, MAX_VALUE_SIZE_BYTES)
            );
        }
    }
    
    /**
     * Estimates the size of a key in bytes.
     */
    private long estimateKeySize(String key) {
        return key.getBytes(StandardCharsets.UTF_8).length;
    }
    
    /**
     * Checks if eviction is needed to accommodate a new entry.
     */
    private boolean needsEviction(long newEntrySize) {
        long projectedUsage = currentMemoryUsage.get() + newEntrySize;
        return (double) projectedUsage / capacity >= EVICTION_THRESHOLD;
    }
    
    /**
     * Performs eviction to free up space.
     * Uses write lock to ensure thread safety during eviction.
     */
    private void performEviction(long newEntrySize) {
        if (evictionPolicy == null) {
            throw new IllegalStateException("Cannot perform eviction without an eviction policy");
        }
        
        evictionLock.writeLock().lock();
        try {
            // Calculate target bytes to free (10% of capacity + space for new entry)
            long targetBytes = (long) (capacity * EvictionPolicy.EVICTION_TARGET) + newEntrySize;
            
            // Get victims from eviction policy
            Set<String> victims = evictionPolicy.selectVictims(targetBytes);
            
            // Remove victims
            for (String key : victims) {
                CacheEntry<V> entry = storage.remove(key);
                if (entry != null) {
                    currentMemoryUsage.addAndGet(-entry.getSizeBytes());
                    evictionPolicy.onRemove(key);
                }
            }
        } finally {
            evictionLock.writeLock().unlock();
        }
    }
    
    /**
     * Gets the underlying storage map (for testing purposes).
     */
    ConcurrentHashMap<String, CacheEntry<V>> getStorage() {
        return storage;
    }
}
