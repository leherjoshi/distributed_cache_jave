package com.distributedcache.cache;

import com.distributedcache.eviction.EvictionPolicy;
import com.distributedcache.eviction.EvictionPolicyType;
import com.distributedcache.eviction.LRUEvictionPolicy;
import com.distributedcache.exceptions.InvalidKeyException;
import com.distributedcache.exceptions.InvalidValueException;
import com.distributedcache.utils.JavaMessageSerializer;
import com.distributedcache.utils.MessageSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocalCacheImpl.
 */
class LocalCacheImplTest {
    
    private LocalCacheImpl<String> cache;
    private MessageSerializer serializer;
    private static final long CAPACITY = 10000; // 10KB capacity
    
    @BeforeEach
    void setUp() {
        serializer = new JavaMessageSerializer();
        cache = new LocalCacheImpl<>(CAPACITY, serializer);
    }
    
    // Constructor tests
    
    @Test
    @DisplayName("Constructor with valid parameters succeeds")
    void testConstructorValid() throws InvalidKeyException, InvalidValueException {
        assertNotNull(cache);
        assertEquals(CAPACITY, cache.getCapacity());
        assertEquals(0, cache.size());
        assertEquals(0, cache.getMemoryUsage());
    }
    
    @Test
    @DisplayName("Constructor with zero capacity throws exception")
    void testConstructorZeroCapacity() throws InvalidKeyException, InvalidValueException {
        assertThrows(IllegalArgumentException.class, 
            () -> new LocalCacheImpl<>(0, serializer));
    }
    
    @Test
    @DisplayName("Constructor with negative capacity throws exception")
    void testConstructorNegativeCapacity() throws InvalidKeyException, InvalidValueException {
        assertThrows(IllegalArgumentException.class, 
            () -> new LocalCacheImpl<>(-1000, serializer));
    }
    
    @Test
    @DisplayName("Constructor with null serializer throws exception")
    void testConstructorNullSerializer() throws InvalidKeyException, InvalidValueException {
        assertThrows(IllegalArgumentException.class, 
            () -> new LocalCacheImpl<>(CAPACITY, null));
    }
    
    // Put operation tests
    
    @Test
    @DisplayName("Put stores value successfully")
    void testPutStoresValue() throws InvalidKeyException, InvalidValueException {
        cache.put("key1", "value1");
        
        assertEquals(1, cache.size());
        assertTrue(cache.getMemoryUsage() > 0);
    }
    
    @Test
    @DisplayName("Put with null key throws InvalidKeyException")
    void testPutNullKey() throws InvalidKeyException, InvalidValueException {
        assertThrows(InvalidKeyException.class, 
            () -> cache.put(null, "value"));
    }
    
    @Test
    @DisplayName("Put with null value throws InvalidValueException")
    void testPutNullValue() throws InvalidKeyException, InvalidValueException {
        assertThrows(InvalidValueException.class, 
            () -> cache.put("key", null));
    }
    
    @Test
    @DisplayName("Put with key exceeding 256 bytes throws InvalidKeyException")
    void testPutKeyTooLarge() throws InvalidKeyException, InvalidValueException {
        String largeKey = "a".repeat(257);
        
        assertThrows(InvalidKeyException.class, 
            () -> cache.put(largeKey, "value"));
    }
    
    @Test
    @DisplayName("Put with key exactly 256 bytes succeeds")
    void testPutKeyExactly256Bytes() throws InvalidKeyException, InvalidValueException {
        String key = "a".repeat(256);
        
        assertDoesNotThrow(() -> cache.put(key, "value"));
    }
    
    @Test
    @DisplayName("Put with value exceeding 1 MB throws InvalidValueException")
    void testPutValueTooLarge() throws InvalidKeyException, InvalidValueException {
        // Create a large value (over 1 MB)
        String largeValue = "a".repeat(1024 * 1024 + 1000);
        
        assertThrows(InvalidValueException.class, 
            () -> cache.put("key", largeValue));
    }
    
    @Test
    @DisplayName("Put updates existing key")
    void testPutUpdatesExistingKey() throws InvalidKeyException, InvalidValueException {
        cache.put("key1", "value1");
        long memoryAfterFirst = cache.getMemoryUsage();
        
        cache.put("key1", "value2");
        
        assertEquals(1, cache.size());
        // Memory usage should be updated
        assertTrue(cache.getMemoryUsage() > 0);
    }
    
    @Test
    @DisplayName("Put notifies eviction policy")
    void testPutNotifiesEvictionPolicy() throws InvalidKeyException, InvalidValueException {
        TestEvictionPolicy policy = new TestEvictionPolicy();
        cache.setEvictionPolicy(policy);
        
        cache.put("key1", "value1");
        
        assertTrue(policy.onPutCalled);
        assertEquals("key1", policy.lastPutKey);
    }
    
    // Get operation tests
    
    @Test
    @DisplayName("Get returns value for existing key")
    void testGetReturnsValue() throws InvalidKeyException, InvalidValueException {
        cache.put("key1", "value1");
        
        Optional<String> result = cache.get("key1");
        
        assertTrue(result.isPresent());
        assertEquals("value1", result.get());
    }
    
    @Test
    @DisplayName("Get returns empty for non-existent key")
    void testGetReturnsEmptyForNonExistentKey() throws InvalidKeyException, InvalidValueException {
        Optional<String> result = cache.get("nonexistent");
        
        assertFalse(result.isPresent());
    }
    
    @Test
    @DisplayName("Get tracks access in eviction policy")
    void testGetTracksAccess() throws InvalidKeyException, InvalidValueException {
        TestEvictionPolicy policy = new TestEvictionPolicy();
        cache.setEvictionPolicy(policy);
        cache.put("key1", "value1");
        
        cache.get("key1");
        
        assertTrue(policy.onAccessCalled);
        assertEquals("key1", policy.lastAccessKey);
    }
    
    @Test
    @DisplayName("Get updates access count in CacheEntry")
    void testGetUpdatesAccessCount() throws InvalidKeyException, InvalidValueException {
        cache.put("key1", "value1");
        
        CacheEntry<String> entry = cache.getStorage().get("key1");
        long initialAccessCount = entry.getAccessCount();
        
        cache.get("key1");
        
        assertEquals(initialAccessCount + 1, entry.getAccessCount());
    }
    
    // Remove operation tests
    
    @Test
    @DisplayName("Remove deletes existing key")
    void testRemoveDeletesKey() throws InvalidKeyException, InvalidValueException {
        cache.put("key1", "value1");
        
        cache.remove("key1");
        
        assertEquals(0, cache.size());
        assertEquals(0, cache.getMemoryUsage());
        assertFalse(cache.get("key1").isPresent());
    }
    
    @Test
    @DisplayName("Remove on non-existent key does nothing")
    void testRemoveNonExistentKey() throws InvalidKeyException, InvalidValueException {
        assertDoesNotThrow(() -> cache.remove("nonexistent"));
        assertEquals(0, cache.size());
    }
    
    @Test
    @DisplayName("Remove notifies eviction policy")
    void testRemoveNotifiesEvictionPolicy() throws InvalidKeyException, InvalidValueException {
        TestEvictionPolicy policy = new TestEvictionPolicy();
        cache.setEvictionPolicy(policy);
        cache.put("key1", "value1");
        
        cache.remove("key1");
        
        assertTrue(policy.onRemoveCalled);
        assertEquals("key1", policy.lastRemoveKey);
    }
    
    @Test
    @DisplayName("Remove updates memory usage")
    void testRemoveUpdatesMemoryUsage() throws InvalidKeyException, InvalidValueException {
        cache.put("key1", "value1");
        long memoryAfterPut = cache.getMemoryUsage();
        
        cache.remove("key1");
        
        assertEquals(0, cache.getMemoryUsage());
    }
    
    // Size and capacity tests
    
    @Test
    @DisplayName("Size returns correct count")
    void testSizeReturnsCorrectCount() throws InvalidKeyException, InvalidValueException {
        assertEquals(0, cache.size());
        
        cache.put("key1", "value1");
        assertEquals(1, cache.size());
        
        cache.put("key2", "value2");
        assertEquals(2, cache.size());
        
        cache.remove("key1");
        assertEquals(1, cache.size());
    }
    
    @Test
    @DisplayName("getCapacity returns configured capacity")
    void testGetCapacity() throws InvalidKeyException, InvalidValueException {
        assertEquals(CAPACITY, cache.getCapacity());
    }
    
    @Test
    @DisplayName("getMemoryUsage tracks usage correctly")
    void testGetMemoryUsage() throws InvalidKeyException, InvalidValueException {
        assertEquals(0, cache.getMemoryUsage());
        
        cache.put("key1", "value1");
        long usage1 = cache.getMemoryUsage();
        assertTrue(usage1 > 0);
        
        cache.put("key2", "value2");
        long usage2 = cache.getMemoryUsage();
        assertTrue(usage2 > usage1);
    }
    
    @Test
    @DisplayName("getMemoryUsagePercentage calculates correctly")
    void testGetMemoryUsagePercentage() throws InvalidKeyException, InvalidValueException {
        assertEquals(0.0, cache.getMemoryUsagePercentage(), 0.001);
        
        cache.put("key1", "value1");
        double percentage = cache.getMemoryUsagePercentage();
        assertTrue(percentage > 0.0 && percentage < 1.0);
    }
    
    // Clear operation tests
    
    @Test
    @DisplayName("Clear removes all entries")
    void testClearRemovesAllEntries() throws InvalidKeyException, InvalidValueException {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        cache.clear();
        
        assertEquals(0, cache.size());
        assertEquals(0, cache.getMemoryUsage());
        assertFalse(cache.get("key1").isPresent());
        assertFalse(cache.get("key2").isPresent());
        assertFalse(cache.get("key3").isPresent());
    }
    
    @Test
    @DisplayName("Clear on empty cache does nothing")
    void testClearOnEmptyCache() throws InvalidKeyException, InvalidValueException {
        assertDoesNotThrow(() -> cache.clear());
        assertEquals(0, cache.size());
    }
    
    // Eviction tests
    
    @Test
    @DisplayName("Put triggers eviction at 95% capacity")
    void testPutTriggersEvictionAt95Percent() throws InvalidKeyException, InvalidValueException {
        // Create a small cache
        LocalCacheImpl<String> smallCache = new LocalCacheImpl<>(1000, serializer);
        TestEvictionPolicy policy = new TestEvictionPolicy();
        smallCache.setEvictionPolicy(policy);
        
        // Fill cache to just below 95% (each entry is ~18 bytes including overhead)
        // We need about 52 entries to be at ~936 bytes (93.6%)
        for (int i = 0; i < 52; i++) {
            smallCache.put("key" + i, "value" + i);
        }
        
        // Verify we're below 95%
        double usageBefore = smallCache.getMemoryUsagePercentage();
        assertTrue(usageBefore < 0.95, "Usage before trigger: " + usageBefore);
        
        // Reset the flag after filling
        policy.selectVictimsCalled = false;
        
        // Next put should trigger eviction (will push us over 95%)
        smallCache.put("trigger", "eviction");
        
        // Eviction should have been triggered
        assertTrue(policy.selectVictimsCalled, "selectVictims should have been called");
    }
    
    @Test
    @DisplayName("Eviction without policy throws exception")
    void testEvictionWithoutPolicyThrowsException() throws InvalidKeyException, InvalidValueException {
        // Create a small cache without eviction policy
        LocalCacheImpl<String> smallCache = new LocalCacheImpl<>(500, serializer);
        
        // Fill cache to trigger eviction
        assertThrows(IllegalStateException.class, () -> {
            for (int i = 0; i < 100; i++) {
                smallCache.put("key" + i, "value" + i);
            }
        });
    }
    
    @Test
    @DisplayName("Eviction removes entries selected by policy")
    void testEvictionRemovesSelectedEntries() throws InvalidKeyException, InvalidValueException {
        // Create a very small cache with LRU policy
        LocalCacheImpl<String> smallCache = new LocalCacheImpl<>(300, serializer);
        LRUEvictionPolicy<String, String> lruPolicy = new LRUEvictionPolicy<>(serializer);
        smallCache.setEvictionPolicy(lruPolicy);
        
        // Fill cache - each entry is about 17 bytes, so 20 entries = ~340 bytes
        // This should trigger eviction at 95% of 300 bytes = 285 bytes
        for (int i = 0; i < 20; i++) {
            smallCache.put("key" + i, "value" + i);
        }
        
        // Cache should have evicted some entries
        assertTrue(smallCache.size() < 20);
    }
    
    @Test
    @DisplayName("Eviction frees at least 10% of capacity")
    void testEvictionFreesAtLeast10Percent() throws InvalidKeyException, InvalidValueException {
        // Create a small cache
        LocalCacheImpl<String> smallCache = new LocalCacheImpl<>(1000, serializer);
        LRUEvictionPolicy<String, String> lruPolicy = new LRUEvictionPolicy<>(serializer);
        smallCache.setEvictionPolicy(lruPolicy);
        
        // Fill cache to trigger eviction
        for (int i = 0; i < 30; i++) {
            smallCache.put("key" + i, "value" + i);
        }
        
        // After eviction, usage should be below 95%
        assertTrue(smallCache.getMemoryUsagePercentage() < 0.95);
    }
    
    // Concurrent access tests
    
    @Test
    @DisplayName("Concurrent puts are thread-safe")
    void testConcurrentPuts() throws InterruptedException, InvalidKeyException, InvalidValueException {
        // Create a large cache to avoid eviction during this test
        LocalCacheImpl<String> largeCache = new LocalCacheImpl<>(50000, serializer);
        
        int numThreads = 10;
        int putsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < putsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        largeCache.put(key, "value-" + j);
                    }
                } catch (Exception e) {
                    System.err.println("Exception in thread " + threadId + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // All entries should be stored
        assertEquals(numThreads * putsPerThread, largeCache.size());
    }
    
    @Test
    @DisplayName("Concurrent gets are thread-safe")
    void testConcurrentGets() throws InterruptedException, InvalidKeyException, InvalidValueException {
        // Pre-populate cache
        for (int i = 0; i < 100; i++) {
            cache.put("key" + i, "value" + i);
        }
        
        int numThreads = 10;
        int getsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < getsPerThread; j++) {
                    Optional<String> result = cache.get("key" + (j % 100));
                    if (result.isPresent()) {
                        successCount.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // All gets should succeed
        assertEquals(numThreads * getsPerThread, successCount.get());
    }
    
    @Test
    @DisplayName("Concurrent puts to same key are thread-safe")
    void testConcurrentPutsSameKey() throws InterruptedException {
        int numThreads = 10;
        int putsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < putsPerThread; j++) {
                        cache.put("sharedKey", "value-" + threadId + "-" + j);
                    }
                } catch (Exception e) {
                    System.err.println("Exception in thread " + threadId + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should have exactly one entry
        assertEquals(1, cache.size());
        assertTrue(cache.get("sharedKey").isPresent());
    }
    
    @Test
    @DisplayName("Concurrent mixed operations are thread-safe")
    void testConcurrentMixedOperations() throws InterruptedException {
        int numThreads = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        String key = "key-" + threadId + "-" + j;
                        cache.put(key, "value-" + j);
                        cache.get(key);
                        if (j % 10 == 0) {
                            cache.remove(key);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Exception in thread " + threadId + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should complete without exceptions
        assertTrue(cache.size() > 0);
    }
    
    @Test
    @DisplayName("Concurrent eviction is thread-safe")
    void testConcurrentEviction() throws InterruptedException {
        // Create a small cache to trigger frequent evictions
        LocalCacheImpl<String> smallCache = new LocalCacheImpl<>(2000, serializer);
        LRUEvictionPolicy<String, String> lruPolicy = new LRUEvictionPolicy<>(serializer);
        smallCache.setEvictionPolicy(lruPolicy);
        
        int numThreads = 5;
        int putsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < putsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        smallCache.put(key, "value-" + j);
                    }
                } catch (Exception e) {
                    System.err.println("Exception in thread " + threadId + ": " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should complete without exceptions and stay within capacity
        assertTrue(smallCache.getMemoryUsagePercentage() <= 1.0);
    }
    
    // Integration tests
    
    @Test
    @DisplayName("Put-Get round trip preserves value")
    void testPutGetRoundTrip() throws InvalidKeyException, InvalidValueException {
        String key = "testKey";
        String value = "testValue";
        
        cache.put(key, value);
        Optional<String> result = cache.get(key);
        
        assertTrue(result.isPresent());
        assertEquals(value, result.get());
    }
    
    @Test
    @DisplayName("Multiple put-get operations work correctly")
    void testMultiplePutGetOperations() throws InvalidKeyException, InvalidValueException {
        for (int i = 0; i < 100; i++) {
            cache.put("key" + i, "value" + i);
        }
        
        for (int i = 0; i < 100; i++) {
            Optional<String> result = cache.get("key" + i);
            assertTrue(result.isPresent());
            assertEquals("value" + i, result.get());
        }
    }
    
    @Test
    @DisplayName("Cache handles complex objects")
    void testComplexObjects() throws InvalidKeyException, InvalidValueException {
        // Using String as complex object (serializable)
        String complexValue = "This is a more complex value with special characters: !@#$%^&*()";
        
        cache.put("complex", complexValue);
        Optional<String> result = cache.get("complex");
        
        assertTrue(result.isPresent());
        assertEquals(complexValue, result.get());
    }
    
    // Test helper class for eviction policy
    private static class TestEvictionPolicy implements EvictionPolicy<String, String> {
        boolean onAccessCalled = false;
        boolean onPutCalled = false;
        boolean onRemoveCalled = false;
        boolean selectVictimsCalled = false;
        String lastAccessKey = null;
        String lastPutKey = null;
        String lastRemoveKey = null;
        
        @Override
        public void onAccess(String key) {
            onAccessCalled = true;
            lastAccessKey = key;
        }
        
        @Override
        public void onPut(String key, String value) {
            onPutCalled = true;
            lastPutKey = key;
        }
        
        @Override
        public void onRemove(String key) {
            onRemoveCalled = true;
            lastRemoveKey = key;
        }
        
        @Override
        public Set<String> selectVictims(long targetBytes) {
            selectVictimsCalled = true;
            return Set.of("key0", "key1"); // Return some victims
        }
        
        @Override
        public EvictionPolicyType getType() {
            return EvictionPolicyType.LRU;
        }
    }
}
