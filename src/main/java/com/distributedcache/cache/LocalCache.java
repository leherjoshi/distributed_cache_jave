package com.distributedcache.cache;

import com.distributedcache.eviction.EvictionPolicy;
import com.distributedcache.exceptions.InvalidKeyException;
import com.distributedcache.exceptions.InvalidValueException;
import java.util.Optional;
import java.util.Set;

/**
 * Thread-safe local cache storage with eviction support.
 */
public interface LocalCache<K, V> {
    
    /**
     * Stores a key-value pair.
     * Triggers eviction if capacity threshold is reached.
     * 
     * @throws InvalidKeyException if the key is invalid
     * @throws InvalidValueException if the value is invalid
     */
    void put(K key, V value) throws InvalidKeyException, InvalidValueException;
    
    /**
     * Retrieves a value by key.
     */
    Optional<V> get(K key);
    
    /**
     * Removes a key-value pair.
     */
    void remove(K key);
    
    /**
     * Returns the current number of entries.
     */
    int size();
    
    /**
     * Returns the current memory usage in bytes.
     */
    long getMemoryUsage();
    
    /**
     * Returns the configured capacity in bytes.
     */
    long getCapacity();
    
    /**
     * Returns the current memory usage as a percentage.
     */
    double getMemoryUsagePercentage();
    
    /**
     * Clears all entries.
     */
    void clear();
    
    /**
     * Sets the eviction policy.
     */
    void setEvictionPolicy(EvictionPolicy<K, V> policy);
    
    /**
     * Returns a snapshot of all keys currently in the cache.
     * This is used for rebalancing operations.
     * 
     * @return set of all keys in the cache
     */
    Set<K> keySet();
}
