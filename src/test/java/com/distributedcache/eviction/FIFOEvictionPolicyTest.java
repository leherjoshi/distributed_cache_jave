package com.distributedcache.eviction;

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
 * Unit tests for FIFOEvictionPolicy.
 * 
 * Tests verify that the FIFO eviction policy correctly:
 * - Tracks insertion order
 * - Selects oldest entries for eviction
 * - Handles concurrent operations safely
 * - Manages entry lifecycle (put, remove)
 */
class FIFOEvictionPolicyTest {
    
    private FIFOEvictionPolicy<String, String> policy;
    
    @BeforeEach
    void setUp() {
        policy = new FIFOEvictionPolicy<>();
    }
    
    @Test
    @DisplayName("getType returns FIFO")
    void testGetType() {
        assertEquals(EvictionPolicyType.FIFO, policy.getType());
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
        assertEquals(1, policy.size());
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
        // Should only have one entry for this key
        assertEquals(1, policy.size());
    }
    
    @Test
    @DisplayName("onPut with null key does not throw exception")
    void testOnPutWithNullKey() {
        assertDoesNotThrow(() -> policy.onPut(null, "value"));
        assertEquals(0, policy.size());
    }
    
    @Test
    @DisplayName("onAccess does not affect FIFO order")
    void testOnAccessDoesNotAffectOrder() {
        // Add three entries
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Access key1 multiple times (should not affect FIFO order)
        policy.onAccess("key1");
        policy.onAccess("key1");
        policy.onAccess("key1");
        
        // Select one victim with small target - should still be key1 (oldest)
        Set<String> victims = policy.selectVictims(1);
        
        // Should select exactly one entry
        assertEquals(1, victims.size());
        // key1 should be selected as it's the oldest (first inserted)
        assertTrue(victims.contains("key1"));
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
        assertEquals(1, policy.size());
        
        policy.onRemove(key);
        assertEquals(0, policy.size());
        
        // Entry should not be in victims
        Set<String> victims = policy.selectVictims(1000);
        assertFalse(victims.contains(key));
    }
    
    @Test
    @DisplayName("onRemove on non-existent key does not throw exception")
    void testOnRemoveNonExistentKey() {
        assertDoesNotThrow(() -> policy.onRemove("nonexistent"));
        assertEquals(0, policy.size());
    }
    
    @Test
    @DisplayName("onRemove with null key does not throw exception")
    void testOnRemoveWithNullKey() {
        assertDoesNotThrow(() -> policy.onRemove(null));
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
    @DisplayName("selectVictims selects oldest entry first")
    void testSelectVictimsSelectsOldestFirst() {
        // Add entries in order
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // key1 is the oldest (first inserted)
        Set<String> victims = policy.selectVictims(100);
        
        // Should select key1 first
        assertTrue(victims.contains("key1"));
    }
    
    @Test
    @DisplayName("selectVictims maintains FIFO order across multiple entries")
    void testSelectVictimsMaintainsFIFOOrder() {
        // Add entries in specific order
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        policy.onPut("key4", "value4");
        
        // Request small target to evict only first entry
        Set<String> victims = policy.selectVictims(1);
        
        // Should select key1 (oldest entry)
        assertTrue(victims.contains("key1"));
        // key4 should not be selected (newest)
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
    @DisplayName("FIFO order is maintained after multiple accesses")
    void testFIFOOrderAfterMultipleAccesses() {
        // Add entries
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Access key3 multiple times (should not affect FIFO order)
        policy.onAccess("key3");
        policy.onAccess("key3");
        policy.onAccess("key3");
        
        // key1 is still the oldest
        Set<String> victims = policy.selectVictims(1);
        
        // Should select exactly one entry
        assertEquals(1, victims.size());
        // Should select key1 (oldest)
        assertTrue(victims.contains("key1"));
    }
    
    @Test
    @DisplayName("onPut moves existing entry to end of queue")
    void testOnPutMovesEntryToEnd() {
        // Add entries
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Update key1 with new value (should move to end)
        policy.onPut("key1", "newvalue1");
        
        // key2 should now be the oldest
        Set<String> victims = policy.selectVictims(1);
        
        // Should select exactly one entry
        assertEquals(1, victims.size());
        // Should select key2 (now oldest)
        assertTrue(victims.contains("key2"));
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
        assertEquals(numThreads * putsPerThread, policy.size());
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
    @DisplayName("Thread safety: concurrent put and eviction")
    void testConcurrentPutAndEviction() throws InterruptedException {
        // Pre-populate with entries
        for (int i = 0; i < 50; i++) {
            policy.onPut("key" + i, "value" + i);
        }
        
        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        // Half threads do put, half do eviction
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                if (threadId % 2 == 0) {
                    // Put operations
                    for (int j = 0; j < 100; j++) {
                        policy.onPut("newkey" + (threadId * 100 + j), "value" + j);
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
        
        assertEquals(numEntries, policy.size());
        
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
        
        // Select victims
        Set<String> victims = policy.selectVictims(100);
        
        // Small should be selected first as it's the oldest
        assertTrue(victims.contains("small"));
    }
    
    @Test
    @DisplayName("Repeated onPut with same key maintains single entry")
    void testRepeatedOnPutSameKey() {
        String key = "key1";
        
        policy.onPut(key, "value1");
        policy.onPut(key, "value2");
        policy.onPut(key, "value3");
        
        // Should only have one entry for this key
        assertEquals(1, policy.size());
        
        Set<String> victims = policy.selectVictims(Long.MAX_VALUE);
        
        // Count occurrences of key1
        long count = victims.stream().filter(k -> k.equals(key)).count();
        assertEquals(1, count);
    }
    
    @Test
    @DisplayName("Insertion order is preserved after remove and re-add")
    void testInsertionOrderAfterRemoveAndReAdd() {
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Remove and re-add key1
        policy.onRemove("key1");
        policy.onPut("key1", "value1");
        
        // key1 should now be newest (at the end)
        Set<String> victims = policy.selectVictims(100);
        
        // key2 should be selected as oldest
        assertTrue(victims.contains("key2"));
    }
    
    @Test
    @DisplayName("selectVictims returns entries in insertion order")
    void testSelectVictimsReturnsInInsertionOrder() {
        // Add entries in specific order
        policy.onPut("first", "value1");
        policy.onPut("second", "value2");
        policy.onPut("third", "value3");
        policy.onPut("fourth", "value4");
        
        // Request all entries
        Set<String> victims = policy.selectVictims(Long.MAX_VALUE);
        
        // Convert to array to check order
        String[] victimsArray = victims.toArray(new String[0]);
        
        // Should be in insertion order
        assertEquals("first", victimsArray[0]);
        assertEquals("second", victimsArray[1]);
        assertEquals("third", victimsArray[2]);
        assertEquals("fourth", victimsArray[3]);
    }
    
    @Test
    @DisplayName("Empty policy has size zero")
    void testEmptyPolicySize() {
        assertEquals(0, policy.size());
    }
    
    @Test
    @DisplayName("Size increases with onPut")
    void testSizeIncreasesWithOnPut() {
        assertEquals(0, policy.size());
        
        policy.onPut("key1", "value1");
        assertEquals(1, policy.size());
        
        policy.onPut("key2", "value2");
        assertEquals(2, policy.size());
        
        policy.onPut("key3", "value3");
        assertEquals(3, policy.size());
    }
    
    @Test
    @DisplayName("Size decreases with onRemove")
    void testSizeDecreasesWithOnRemove() {
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        assertEquals(3, policy.size());
        
        policy.onRemove("key1");
        assertEquals(2, policy.size());
        
        policy.onRemove("key2");
        assertEquals(1, policy.size());
        
        policy.onRemove("key3");
        assertEquals(0, policy.size());
    }
    
    @Test
    @DisplayName("FIFO eviction with byte[] values")
    void testFIFOWithByteArrayValues() {
        policy.onPut("key1", new String(new byte[100]));
        policy.onPut("key2", new String(new byte[200]));
        policy.onPut("key3", new String(new byte[300]));
        
        // Select victims
        Set<String> victims = policy.selectVictims(150);
        
        // Should select key1 first (oldest)
        assertTrue(victims.contains("key1"));
    }
    
    @Test
    @DisplayName("FIFO eviction with Integer values")
    void testFIFOWithIntegerValues() {
        FIFOEvictionPolicy<String, Integer> intPolicy = new FIFOEvictionPolicy<>();
        
        intPolicy.onPut("key1", 100);
        intPolicy.onPut("key2", 200);
        intPolicy.onPut("key3", 300);
        
        // Select victims
        Set<String> victims = intPolicy.selectVictims(50);
        
        // Should select key1 first (oldest)
        assertTrue(victims.contains("key1"));
    }
}
