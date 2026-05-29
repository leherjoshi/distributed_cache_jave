package com.distributedcache.eviction;

import java.util.Set;

/**
 * Strategy interface for cache eviction policies.
 */
public interface EvictionPolicy<K, V> {
    
    /**
     * Threshold percentage that triggers eviction.
     */
    double EVICTION_THRESHOLD = 0.95;
    
    /**
     * Minimum percentage to free when eviction is triggered.
     */
    double EVICTION_TARGET = 0.10;
    
    /**
     * Called when an entry is accessed (get operation).
     */
    void onAccess(K key);
    
    /**
     * Called when an entry is added or updated.
     */
    void onPut(K key, V value);
    
    /**
     * Called when an entry is removed.
     */
    void onRemove(K key);
    
    /**
     * Selects entries to evict to free the target percentage of capacity.
     * 
     * @param targetBytes number of bytes to free
     * @return set of keys to evict
     */
    Set<K> selectVictims(long targetBytes);
    
    /**
     * Returns the eviction policy type.
     */
    EvictionPolicyType getType();
}
