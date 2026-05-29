package com.distributedcache.eviction;

/**
 * Supported eviction policy types.
 */
public enum EvictionPolicyType {
    LRU,  // Least Recently Used
    LFU,  // Least Frequently Used
    FIFO  // First In First Out
}
