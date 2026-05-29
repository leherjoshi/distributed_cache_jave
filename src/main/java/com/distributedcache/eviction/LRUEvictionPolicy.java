package com.distributedcache.eviction;

import com.distributedcache.utils.MessageSerializer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU (Least Recently Used) eviction policy implementation.
 * Uses LinkedHashMap with access-order for O(1) LRU tracking.
 * Thread-safe using ReadWriteLock.
 */
public class LRUEvictionPolicy<K, V> implements EvictionPolicy<K, V> {
    
    private final LinkedHashMap<K, Long> accessOrder;
    private final ReadWriteLock lock;
    private final MessageSerializer serializer;
    
    /**
     * Creates a new LRU eviction policy.
     * 
     * @param serializer the serializer for estimating object sizes
     */
    public LRUEvictionPolicy(MessageSerializer serializer) {
        // LinkedHashMap with access-order=true moves accessed entries to the end
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true);
        this.lock = new ReentrantReadWriteLock();
        this.serializer = serializer;
    }
    
    @Override
    public void onAccess(K key) {
        lock.writeLock().lock();
        try {
            // Remove and re-add to move to end (most recently used)
            Long size = accessOrder.remove(key);
            if (size != null) {
                accessOrder.put(key, size);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void onPut(K key, V value) {
        lock.writeLock().lock();
        try {
            long size = serializer.estimateSize(value);
            // Remove old entry if exists, then add new one
            accessOrder.remove(key);
            accessOrder.put(key, size);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public void onRemove(K key) {
        lock.writeLock().lock();
        try {
            accessOrder.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Set<K> selectVictims(long targetBytes) {
        Set<K> victims = new LinkedHashSet<>();
        long freedBytes = 0;
        
        lock.readLock().lock();
        try {
            // Iterate from the beginning (least recently used)
            for (Map.Entry<K, Long> entry : accessOrder.entrySet()) {
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
    
    @Override
    public EvictionPolicyType getType() {
        return EvictionPolicyType.LRU;
    }
}
