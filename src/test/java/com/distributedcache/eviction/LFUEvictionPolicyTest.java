package com.distributedcache.eviction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LFUEvictionPolicy.
 */
class LFUEvictionPolicyTest {
    
    private LFUEvictionPolicy<String, String> policy;
    
    @BeforeEach
    void setUp() {
        policy = new LFUEvictionPolicy<>();
    }
    
    @Test
    @DisplayName("getType returns LFU")
    void testGetType() {
        assertEquals(EvictionPolicyType.LFU, policy.getType());
    }
    
    @Test
    @DisplayName("onAccess increments access count")
    void testOnAccessIncrementsCount() {
        String key = "key1";
        
        // Initial access
        policy.onAccess(key);
        assertEquals(1L, policy.getAccessFrequency(key));
        
        // Second access
        policy.onAccess(key);
        assertEquals(2L, policy.getAccessFrequency(key));
        
        // Third access
        policy.onAccess(key);
        assertEquals(3L, policy.getAccessFrequency(key));
    }
    
    @Test
    @DisplayName("onAccess handles null key gracefully")
    void testOnAccessWithNullKey() {
        assertDoesNotThrow(() -> policy.onAccess(null));
    }
    
    @Test
    @DisplayName("onPut initializes access count to zero")
    void testOnPutInitializesCount() {
        String key = "key1";
        String value = "value1";
        
        policy.onPut(key, value);
        assertEquals(0L, policy.getAccessFrequency(key));
    }
    
    @Test
    @DisplayName("onPut does not reset existing access count")
    void testOnPutDoesNotResetCount() {
        String key = "key1";
        String value = "value1";
        
        // Access the key first
        policy.onAccess(key);
        policy.onAccess(key);
        assertEquals(2L, policy.getAccessFrequency(key));
        
        // Put should not reset the count
        policy.onPut(key, value);
        assertEquals(2L, policy.getAccessFrequency(key));
    }
    
    @Test
    @DisplayName("onPut handles null key gracefully")
    void testOnPutWithNullKey() {
        assertDoesNotThrow(() -> policy.onPut(null, "value"));
    }
    
    @Test
    @DisplayName("onRemove clears tracking data")
    void testOnRemoveClearsData() {
        String key = "key1";
        String value = "value1";
        
        policy.onPut(key, value);
        policy.onAccess(key);
        assertEquals(1L, policy.getAccessFrequency(key));
        
        policy.onRemove(key);
        assertEquals(0L, policy.getAccessFrequency(key));
    }
    
    @Test
    @DisplayName("onRemove handles null key gracefully")
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
    @DisplayName("selectVictims selects least frequently used entry")
    void testSelectVictimsSelectsLeastFrequentlyUsed() {
        // Add entries with different access frequencies
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Access key1 once
        policy.onAccess("key1");
        
        // Access key2 three times
        policy.onAccess("key2");
        policy.onAccess("key2");
        policy.onAccess("key2");
        
        // Access key3 twice
        policy.onAccess("key3");
        policy.onAccess("key3");
        
        // Frequencies: key1=1, key2=3, key3=2
        // Request eviction of one entry
        Set<String> victims = policy.selectVictims(100);
        
        // Should select key1 (lowest frequency)
        assertTrue(victims.contains("key1"));
    }
    
    @Test
    @DisplayName("selectVictims selects multiple entries when needed")
    void testSelectVictimsSelectsMultipleEntries() {
        // Add entries with different access frequencies
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // Access key1 once
        policy.onAccess("key1");
        
        // Access key2 three times
        policy.onAccess("key2");
        policy.onAccess("key2");
        policy.onAccess("key2");
        
        // Access key3 twice
        policy.onAccess("key3");
        policy.onAccess("key3");
        
        // Request eviction of large amount to force multiple victims
        Set<String> victims = policy.selectVictims(500);
        
        // Should select entries in order: key1 (freq=1), key3 (freq=2), key2 (freq=3)
        assertTrue(victims.size() >= 2);
        assertTrue(victims.contains("key1"));
    }
    
    @Test
    @DisplayName("selectVictims maintains order by frequency")
    void testSelectVictimsMaintainsFrequencyOrder() {
        // Add entries with different access frequencies
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        policy.onPut("key4", "value4");
        
        // Set up frequencies: key1=0, key2=1, key3=2, key4=3
        policy.onAccess("key2");
        
        policy.onAccess("key3");
        policy.onAccess("key3");
        
        policy.onAccess("key4");
        policy.onAccess("key4");
        policy.onAccess("key4");
        
        // Request eviction of two entries
        Set<String> victims = policy.selectVictims(200);
        
        // Should select key1 and key2 (lowest frequencies)
        assertTrue(victims.contains("key1"));
        assertTrue(victims.contains("key2"));
        assertFalse(victims.contains("key4")); // Highest frequency should not be selected
    }
    
    @Test
    @DisplayName("selectVictims handles entries with same frequency")
    void testSelectVictimsWithSameFrequency() {
        // Add entries with same access frequency
        policy.onPut("key1", "value1");
        policy.onPut("key2", "value2");
        policy.onPut("key3", "value3");
        
        // All have frequency 0
        
        // Request eviction of one entry
        Set<String> victims = policy.selectVictims(100);
        
        // Should select at least one entry
        assertFalse(victims.isEmpty());
    }
    
    @Test
    @DisplayName("Thread safety: concurrent onAccess calls")
    void testConcurrentOnAccess() throws InterruptedException {
        String key = "key1";
        int numThreads = 10;
        int accessesPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < accessesPerThread; j++) {
                    policy.onAccess(key);
                }
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should have exactly numThreads * accessesPerThread accesses
        assertEquals(numThreads * accessesPerThread, policy.getAccessFrequency(key));
    }
    
    @Test
    @DisplayName("Thread safety: concurrent onPut calls")
    void testConcurrentOnPut() throws InterruptedException {
        int numThreads = 10;
        int putsPerThread = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger counter = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < putsPerThread; j++) {
                    String key = "key" + counter.incrementAndGet();
                    policy.onPut(key, "value");
                }
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should have exactly numThreads * putsPerThread entries
        assertEquals(numThreads * putsPerThread, policy.size());
    }
    
    @Test
    @DisplayName("Thread safety: concurrent selectVictims calls")
    void testConcurrentSelectVictims() throws InterruptedException {
        // Add some entries
        for (int i = 0; i < 100; i++) {
            policy.onPut("key" + i, "value" + i);
            for (int j = 0; j < i; j++) {
                policy.onAccess("key" + i);
            }
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
                    policy.onPut(key, "value");
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
        assertTrue(policy.size() >= 0);
    }
    
    @Test
    @DisplayName("Size tracking works correctly")
    void testSizeTracking() {
        assertEquals(0, policy.size());
        
        policy.onPut("key1", "value1");
        assertEquals(1, policy.size());
        
        policy.onPut("key2", "value2");
        assertEquals(2, policy.size());
        
        policy.onRemove("key1");
        assertEquals(1, policy.size());
        
        policy.onRemove("key2");
        assertEquals(0, policy.size());
    }
    
    @Test
    @DisplayName("Handles large number of entries")
    void testLargeNumberOfEntries() {
        int numEntries = 10000;
        
        for (int i = 0; i < numEntries; i++) {
            policy.onPut("key" + i, "value" + i);
            // Vary access frequency
            for (int j = 0; j < (i % 10); j++) {
                policy.onAccess("key" + i);
            }
        }
        
        assertEquals(numEntries, policy.size());
        
        // Select victims should work efficiently
        Set<String> victims = policy.selectVictims(1000);
        assertFalse(victims.isEmpty());
    }
}
