package com.distributedcache.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetricsCollectorImpl.
 */
class MetricsCollectorImplTest {
    
    private MetricsCollectorImpl metricsCollector;
    
    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollectorImpl();
    }
    
    @Test
    @DisplayName("Initial metrics should be zero")
    void testInitialMetrics() {
        CacheMetrics metrics = metricsCollector.getMetrics();
        
        assertEquals(0, metrics.getTotalHits());
        assertEquals(0, metrics.getTotalMisses());
        assertEquals(0.0, metrics.getAverageGetLatencyMs(), 0.001);
        assertEquals(0.0, metrics.getMemoryUsagePercentage(), 0.001);
    }
    
    @Test
    @DisplayName("recordHit should increment hit counter")
    void testRecordHit() {
        metricsCollector.recordHit();
        metricsCollector.recordHit();
        metricsCollector.recordHit();
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(3, metrics.getTotalHits());
        assertEquals(0, metrics.getTotalMisses());
    }
    
    @Test
    @DisplayName("recordMiss should increment miss counter")
    void testRecordMiss() {
        metricsCollector.recordMiss();
        metricsCollector.recordMiss();
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(0, metrics.getTotalHits());
        assertEquals(2, metrics.getTotalMisses());
    }
    
    @Test
    @DisplayName("recordHit and recordMiss should work together")
    void testRecordHitAndMiss() {
        metricsCollector.recordHit();
        metricsCollector.recordMiss();
        metricsCollector.recordHit();
        metricsCollector.recordHit();
        metricsCollector.recordMiss();
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(3, metrics.getTotalHits());
        assertEquals(2, metrics.getTotalMisses());
    }
    
    @Test
    @DisplayName("recordGetLatency should calculate average correctly")
    void testRecordGetLatency() {
        metricsCollector.recordGetLatency(10);
        metricsCollector.recordGetLatency(20);
        metricsCollector.recordGetLatency(30);
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(20.0, metrics.getAverageGetLatencyMs(), 0.001);
    }
    
    @Test
    @DisplayName("recordGetLatency with single value")
    void testRecordGetLatencySingleValue() {
        metricsCollector.recordGetLatency(15);
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(15.0, metrics.getAverageGetLatencyMs(), 0.001);
    }
    
    @Test
    @DisplayName("recordGetLatency should throw exception for negative latency")
    void testRecordGetLatencyNegative() {
        assertThrows(IllegalArgumentException.class, () -> {
            metricsCollector.recordGetLatency(-5);
        });
    }
    
    @Test
    @DisplayName("recordMemoryUsage should calculate percentage correctly")
    void testRecordMemoryUsage() {
        metricsCollector.recordMemoryUsage(500, 1000);
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(50.0, metrics.getMemoryUsagePercentage(), 0.001);
    }
    
    @Test
    @DisplayName("recordMemoryUsage with different values")
    void testRecordMemoryUsageDifferentValues() {
        metricsCollector.recordMemoryUsage(750, 1000);
        CacheMetrics metrics1 = metricsCollector.getMetrics();
        assertEquals(75.0, metrics1.getMemoryUsagePercentage(), 0.001);
        
        metricsCollector.recordMemoryUsage(250, 1000);
        CacheMetrics metrics2 = metricsCollector.getMetrics();
        assertEquals(25.0, metrics2.getMemoryUsagePercentage(), 0.001);
    }
    
    @Test
    @DisplayName("recordMemoryUsage should throw exception for negative bytes")
    void testRecordMemoryUsageNegativeBytes() {
        assertThrows(IllegalArgumentException.class, () -> {
            metricsCollector.recordMemoryUsage(-100, 1000);
        });
    }
    
    @Test
    @DisplayName("recordMemoryUsage should throw exception for zero capacity")
    void testRecordMemoryUsageZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> {
            metricsCollector.recordMemoryUsage(100, 0);
        });
    }
    
    @Test
    @DisplayName("recordMemoryUsage should throw exception for negative capacity")
    void testRecordMemoryUsageNegativeCapacity() {
        assertThrows(IllegalArgumentException.class, () -> {
            metricsCollector.recordMemoryUsage(100, -1000);
        });
    }
    
    @Test
    @DisplayName("reset should clear all metrics")
    void testReset() {
        metricsCollector.recordHit();
        metricsCollector.recordHit();
        metricsCollector.recordMiss();
        metricsCollector.recordGetLatency(10);
        metricsCollector.recordGetLatency(20);
        metricsCollector.recordMemoryUsage(500, 1000);
        
        metricsCollector.reset();
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(0, metrics.getTotalHits());
        assertEquals(0, metrics.getTotalMisses());
        assertEquals(0.0, metrics.getAverageGetLatencyMs(), 0.001);
        assertEquals(0.0, metrics.getMemoryUsagePercentage(), 0.001);
    }
    
    @Test
    @DisplayName("Hits per second should be calculated using sliding window")
    void testHitsPerSecond() throws InterruptedException {
        // Record some hits
        for (int i = 0; i < 10; i++) {
            metricsCollector.recordHit();
        }
        
        // Wait for a short time (less than window size)
        Thread.sleep(500);
        
        // Record more hits
        for (int i = 0; i < 5; i++) {
            metricsCollector.recordHit();
        }
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        
        // Should have 15 total hits
        assertEquals(15, metrics.getTotalHits());
        
        // Hits per second should be calculated over the time window
        // Since we recorded 15 hits over ~0.5 seconds, rate should be around 30 hits/sec
        // But allow wide tolerance due to timing variations
        assertTrue(metrics.getHitsPerSecond() >= 10.0,
                "Hits per second should be at least 10, but was: " + metrics.getHitsPerSecond());
    }
    
    @Test
    @DisplayName("Misses per second should be calculated using sliding window")
    void testMissesPerSecond() throws InterruptedException {
        // Record some misses
        for (int i = 0; i < 8; i++) {
            metricsCollector.recordMiss();
        }
        
        // Wait for a short time (less than window size)
        Thread.sleep(500);
        
        // Record more misses
        for (int i = 0; i < 4; i++) {
            metricsCollector.recordMiss();
        }
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        
        // Should have 12 total misses
        assertEquals(12, metrics.getTotalMisses());
        
        // Misses per second should be calculated over the time window
        // Since we recorded 12 misses over ~0.5 seconds, rate should be around 24 misses/sec
        // But allow wide tolerance due to timing variations
        assertTrue(metrics.getMissesPerSecond() >= 8.0,
                "Misses per second should be at least 8, but was: " + metrics.getMissesPerSecond());
    }
    
    @Test
    @DisplayName("Concurrent recordHit should be thread-safe")
    void testConcurrentRecordHit() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        metricsCollector.recordHit();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(threadCount * operationsPerThread, metrics.getTotalHits());
    }
    
    @Test
    @DisplayName("Concurrent recordMiss should be thread-safe")
    void testConcurrentRecordMiss() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        metricsCollector.recordMiss();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(threadCount * operationsPerThread, metrics.getTotalMisses());
    }
    
    @Test
    @DisplayName("Concurrent recordGetLatency should be thread-safe")
    void testConcurrentRecordGetLatency() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        metricsCollector.recordGetLatency(threadId + 1);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        // Average should be around 5.5 (average of 1-10)
        assertTrue(metrics.getAverageGetLatencyMs() > 0);
    }
    
    @Test
    @DisplayName("Concurrent mixed operations should be thread-safe")
    void testConcurrentMixedOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger expectedHits = new AtomicInteger(0);
        AtomicInteger expectedMisses = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (threadId % 2 == 0) {
                            metricsCollector.recordHit();
                            expectedHits.incrementAndGet();
                        } else {
                            metricsCollector.recordMiss();
                            expectedMisses.incrementAndGet();
                        }
                        metricsCollector.recordGetLatency(j + 1);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(expectedHits.get(), metrics.getTotalHits());
        assertEquals(expectedMisses.get(), metrics.getTotalMisses());
    }
    
    @Test
    @DisplayName("getMetrics should return consistent snapshot")
    void testGetMetricsSnapshot() {
        metricsCollector.recordHit();
        metricsCollector.recordHit();
        metricsCollector.recordMiss();
        metricsCollector.recordGetLatency(10);
        metricsCollector.recordMemoryUsage(500, 1000);
        
        CacheMetrics metrics1 = metricsCollector.getMetrics();
        CacheMetrics metrics2 = metricsCollector.getMetrics();
        
        // Both snapshots should have the same values
        assertEquals(metrics1.getTotalHits(), metrics2.getTotalHits());
        assertEquals(metrics1.getTotalMisses(), metrics2.getTotalMisses());
        assertEquals(metrics1.getAverageGetLatencyMs(), metrics2.getAverageGetLatencyMs(), 0.001);
        assertEquals(metrics1.getMemoryUsagePercentage(), metrics2.getMemoryUsagePercentage(), 0.001);
    }
    
    @Test
    @DisplayName("Memory usage percentage should handle 100% correctly")
    void testMemoryUsageFullCapacity() {
        metricsCollector.recordMemoryUsage(1000, 1000);
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(100.0, metrics.getMemoryUsagePercentage(), 0.001);
    }
    
    @Test
    @DisplayName("Memory usage percentage should handle 0% correctly")
    void testMemoryUsageZeroUsage() {
        metricsCollector.recordMemoryUsage(0, 1000);
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(0.0, metrics.getMemoryUsagePercentage(), 0.001);
    }
    
    @Test
    @DisplayName("Average latency should handle large values correctly")
    void testAverageLatencyLargeValues() {
        metricsCollector.recordGetLatency(1000);
        metricsCollector.recordGetLatency(2000);
        metricsCollector.recordGetLatency(3000);
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        assertEquals(2000.0, metrics.getAverageGetLatencyMs(), 0.001);
    }
    
    @Test
    @DisplayName("Metrics should update every 1 second as per UPDATE_INTERVAL_MS")
    void testMetricsUpdateInterval() throws InterruptedException {
        // Record initial hits
        metricsCollector.recordHit();
        metricsCollector.recordHit();
        
        CacheMetrics metrics1 = metricsCollector.getMetrics();
        
        // Wait for update interval
        Thread.sleep(MetricsCollector.UPDATE_INTERVAL_MS + 100);
        
        // Record more hits
        metricsCollector.recordHit();
        
        CacheMetrics metrics2 = metricsCollector.getMetrics();
        
        // Total hits should increase
        assertTrue(metrics2.getTotalHits() > metrics1.getTotalHits());
    }
}
