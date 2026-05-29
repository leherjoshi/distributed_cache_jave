package com.distributedcache.eviction;

import com.distributedcache.utils.JavaMessageSerializer;
import com.distributedcache.utils.MessageSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LRUEvictionPolicy.
 */
class LRUEvictionPolicyTest {
    
    private LRUEvictionPolicy<String, String> policy;
    private MessageSerializer serializer;
    
    @BeforeEach
    void setUp() {
        serializer = new JavaMessageSerializer();
        policy = new LRUEvictionPolicy<>(serializer);
    }
    
    @Test
    @DisplayName("getType returns LRU")
    void testGetType() {
        assertEquals(EvictionPolicyType.LRU, policy.getType());
    }
    
    @Test
    @DisplayName("onPut adds entry to tracking")
    void testOnPutAddsEntry() {
        String key = "key1";
        String value = "value1";
        
        policy.onPut(key, value);
        
        // Verify entry is tracked by selecting victims
        Set<String> victims = policy.selectVictims(1000);
        assertTrue(victims.contains(key));
    }
    
    @Test
    @DisplayName("onPut updates existing entry size")
    void testOnPutUpdatesExistingEntry() {
        String key = "key1";
        String value1 = "short";
        String value2 = "much longer value that takes more space";
        
        policy.onPut(key, value1);
        policy.onPut(key, value2);
        
        // Entry should still be tracked
        Set<String> victims = policy.selectVictims(1000);
        assertTrue(victims.contains(key));
    }
    
    @Test
    @DisplayName("onAccess updates access order")
    void testOnAccessUpdatesOrder() {
        // Add three entries
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Access key1 to make it most recently used
        policy.onAccess("key1");
        
        // Select one victim with small target - should be key2 (least recently used)
        // Use a small target to get only one entry
        Set<String> victims = policy.selectVictims(1);
        
        // Should select exactly one entry
        assertEquals(1, victims.size());
        // key2 should be selected as it's the least recently used
        assertTrue(victims.contains("key2"));
        // key1 should not be selected since it was just accessed
        assertFalse(victims.contains("key1"));
    }
    
    @Test
    @DisplayName("onAccess on non-existent key does not throw exception")
    void testOnAccessNonExistentKey() {
        assertDoesNotThrow(() -> policy.onAccess("nonexistent"));
    }
    
    @Test
    @DisplayName("onRemove removes entry from tracking")
    void testOnRemoveRemovesEntry() {
        String key = "key1";
        String value = "value1";
        
        policy.onPut(key, value);
        policy.onRemove(key);
        
        // Entry should not be in victims
        Set<String> victims = policy.selectVictims(1000);
        assertFalse(victims.contains(key));
    }
    
    @Test
    @DisplayName("onRemove on non-existent key does not throw exception")
    void testOnRemoveNonExistentKey() {
        assertDoesNotThrow(() -> policy.onRemove("nonexistent"));
    }
    
    @Test
    @DisplayName("selectVictims returns empty set when targetBytes is zero")
    void testSelectVictimsWithZeroTarget() {
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        
        Set<String> victims = policy.selectVictims(0);
        assertTrue(victims.isEmpty());
    }
    
    @Test
    @DisplayName("selectVictims returns empty set when targetBytes is negative")
    void testSelectVictimsWithNegativeTarget() {
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        
        Set<String> victims = policy.selectVictims(-100);
        assertTrue(victims.isEmpty());
    }
    
    @Test
    @DisplayName("selectVictims returns empty set when no entries exist")
    void testSelectVictimsWithNoEntries() {
        Set<String> victims = policy.selectVictims(1000);
        assertTrue(victims.isEmpty());
    }
    
    @Test
    @DisplayName("selectVictims selects least recently used entry")
    void testSelectVictimsSelectsLRU() {
        // Add entries in order
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Access key2 and key3 to make them more recently used
        policy.onAccess("key2");
        policy.onAccess("key3");
        
        // key1 is now the least recently used
        Set<String> victims = policy.selectVictims(100);
        
        // Should select key1 first
        assertTrue(victims.contains("key1"));
    }
    
    @Test
    @DisplayName("selectVictims maintains LRU order across multiple entries")
    void testSelectVictimsMaintainsLRUOrder() {
        // Add entries
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        policy.onPut("key4", "value4");
        
        // Access in specific order: key3, key1, key4
        // This makes key2 the least recently used, followed by key3, key1, key4
        policy.onAccess("key3");
        policy.onAccess("key1");
        policy.onAccess("key4");
        
        // Request enough bytes to evict at least 2 entries
        Set<String> victims = policy.selectVictims(30);
        
        // Should select key2 (LRU) first
        assertTrue(victims.contains("key2"));
        // key4 should not be selected (most recently used)
        assertFalse(victims.contains("key4"));
    }
    
    @Test
    @DisplayName("selectVictims accumulates bytes until target is met")
    void testSelectVictimsAccumulatesBytes() {
        // Add entries with known sizes
        policy.onPut("key1", "a");
        policy.onPut("key2", "b");
        policy.onPut("key3", "c");
        
        // Request small target - should select at least one entry
        Set<String> victims = policy.selectVictims(50);
        
        assertFalse(victims.isEmpty());
    }
    
    @Test
    @DisplayName("selectVictims selects all entries if target exceeds total size")
    void testSelectVictimsSelectsAllWhenTargetExceedsTotal() {
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Request very large target
        Set<String> victims = policy.selectVictims(Long.MAX_VALUE);
        
        // Should select all entries
        assertEquals(3, victims.size());
        assertTrue(victims.contains("key1"));
        assertTrue(victims.contains("key2"));
        assertTrue(victims.contains("key3"));
    }
    
    @Test
    @DisplayName("LRU order is maintained after multiple accesses")
    void testLRUOrderAfterMultipleAccesses() {
        // Add entries
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Access key1 multiple times
        policy.onAccess("key1");
        policy.onAccess("key1");
        policy.onAccess("key1");
        
        // key2 is still the least recently used
        Set<String> victims = policy.selectVictims(1);
        
        // Should select exactly one entry
        assertEquals(1, victims.size());
        // Should select key2 (LRU)
        assertTrue(victims.contains("key2"));
    }
    
    @Test
    @DisplayName("onPut moves existing entry to most recently used")
    void testOnPutMovesToMostRecentlyUsed() {
        // Add entries
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Update key1 with new value
        policy.onPut("key1", "newvalue1");
        
        // key2 should now be the least recently used
        Set<String> victims = policy.selectVictims(1);
        
        // Should select exactly one entry
        assertEquals(1, victims.size());
        // Should select key2 (LRU)
        assertTrue(victims.contains("key2"));
    }
    
    @Test
    @DisplayName("Thread safety: concurrent onAccess calls")
    void testConcurrentOnAccess() throws InterruptedException {
        // Add entries
        for (int i = 0; i < 10; i++) {
            policy.onPut("key" + i, "value" + i);
        }
        
        int numThreads = 10;
        int accessesPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < accessesPerThread; j++) {
                    policy.onAccess("key" + (threadId % 10));
                }
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should complete without exceptions
        Set<String> victims = policy.selectVictims(100);
        assertNotNull(victims);
    }
    
    @Test
    @DisplayName("Thread safety: concurrent onPut calls")
    void testConcurrentOnPut() throws InterruptedException {
        int numThreads = 10;
        int putsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < putsPerThread; j++) {
                    String key = "key" + (threadId * putsPerThread + j);
                    policy.onPut(key, "value" + j);
                }
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should complete without exceptions
        Set<String> victims = policy.selectVictims(1000);
        assertNotNull(victims);
    }
    
    @Test
    @DisplayName("Thread safety: concurrent selectVictims calls")
    void testConcurrentSelectVictims() throws InterruptedException {
        // Add entries
        for (int i = 0; i < 100; i++) {
            policy.onPut("key" + i, "value" + i);
        }
        
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                Set<String> victims = policy.selectVictims(500);
                assertNotNull(victims);
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
    }
    
    @Test
    @DisplayName("Thread safety: mixed operations")
    void testConcurrentMixedOperations() throws InterruptedException {
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < 50; j++) {
                    String key = "key" + (threadId * 50 + j);
                    policy.onPut(key, "value" + j);
                    policy.onAccess(key);
                    if (j % 10 == 0) {
                        policy.selectVictims(100);
                    }
                    if (j % 20 == 0) {
                        policy.onRemove(key);
                    }
                }
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should complete without exceptions
        assertDoesNotThrow(() -> policy.selectVictims(100));
    }
    
    @Test
    @DisplayName("Thread safety: concurrent access and eviction")
    void testConcurrentAccessAndEviction() throws InterruptedException {
        // Pre-populate with entries
        for (int i = 0; i < 50; i++) {
            policy.onPut("key" + i, "value" + i);
        }
        
        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        // Half threads do access, half do eviction
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                if (threadId % 2 == 0) {
                    // Access operations
                    for (int j = 0; j < 100; j++) {
                        policy.onAccess("key" + (j % 50));
                    }
                } else {
                    // Eviction operations
                    for (int j = 0; j < 100; j++) {
                        policy.selectVictims(200);
                    }
                }
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
    }
    
    @Test
    @DisplayName("Handles large number of entries")
    void testLargeNumberOfEntries() {
        int numEntries = 10000;
        
        for (int i = 0; i < numEntries; i++) {
            policy.onPut("key" + i, "value" + i);
        }
        
        // Select victims should work efficiently
        Set<String> victims = policy.selectVictims(1000);
        assertFalse(victims.isEmpty());
    }
    
    @Test
    @DisplayName("Handles entries with varying sizes")
    void testVaryingSizes() {
        // Add entries with different sizes
        policy.onPut("small", "a");
        policy.onPut("medium", "this is a medium sized value");
        policy.onPut("large", "this is a much larger value that takes up more space in the cache");
        
        // Access small and large to make medium the LRU
        policy.onAccess("small");
        policy.onAccess("large");
        
        // Select victims
        Set<String> victims = policy.selectVictims(100);
        
        // Medium should be selected first as it's the LRU
        assertTrue(victims.contains("medium"));
    }
    
    @Test
    @DisplayName("Repeated onPut with same key maintains single entry")
    void testRepeatedOnPutSameKey() {
        String key = "key1";
        
        policy.onPut(key, "value1");
        policy.onPut(key, "value2");
        policy.onPut(key, "value3");
        
        // Should only have one entry for this key
        Set<String> victims = policy.selectVictims(Long.MAX_VALUE);
        
        // Count occurrences of key1
        long count = victims.stream().filter(k -> k.equals(key)).count();
        assertEquals(1, count);
    }
    
    @Test
    @DisplayName("Access order is preserved after remove and re-add")
    void testAccessOrderAfterRemoveAndReAdd() {
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Access key1 to make it most recently used
        policy.onAccess("key1");
        
        // Remove and re-add key1
        policy.onRemove("key1");
        policy.onPut("key1", "value1");
        
        // key1 should now be most recently used again (due to onPut)
        Set<String> victims = policy.selectVictims(100);
        
        // key2 should be selected as LRU
        assertTrue(victims.contains("key2"));
    }
}
