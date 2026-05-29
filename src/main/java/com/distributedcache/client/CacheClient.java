package com.distributedcache.client;

import com.distributedcache.exceptions.CacheException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client API for interacting with the distributed cache system.
 * Handles routing, retries, and failover transparently.
 */
public interface CacheClient extends AutoCloseable {
    
    /**
     * Retrieves a value from the cache.
     * 
     * @param key the cache key (max 256 bytes)
     * @return Optional containing the value if found, empty otherwise
     * @throws CacheException if operation fails after retries
     */
    <V> Optional<V> get(String key) throws CacheException;
    
    /**
     * Stores a key-value pair in the cache.
     * 
     * @param key the cache key (max 256 bytes)
     * @param value the value to store (max 1 MB)
     * @throws CacheException if operation fails after retries
     */
    <V> void put(String key, V value) throws CacheException;
    
    /**
     * Removes a key from the cache.
     * 
     * @param key the cache key to remove
     * @throws CacheException if operation fails after retries
     */
    void delete(String key) throws CacheException;
    
    /**
     * Retrieves multiple values in a single batch operation.
     * 
     * @param keys the list of keys to retrieve
     * @return map of keys to values (missing keys are omitted)
     * @throws CacheException if operation fails after retries
     */
    <V> Map<String, V> batchGet(List<String> keys) throws CacheException;
    
    /**
     * Asynchronous version of get operation.
     */
    <V> CompletableFuture<Optional<V>> getAsync(String key);
    
    /**
     * Asynchronous version of put operation.
     */
    <V> CompletableFuture<Void> putAsync(String key, V value);
    
    /**
     * Asynchronous version of delete operation.
     */
    CompletableFuture<Void> deleteAsync(String key);
}
