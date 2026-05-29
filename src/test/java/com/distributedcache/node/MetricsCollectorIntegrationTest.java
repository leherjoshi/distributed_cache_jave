package com.distributedcache.node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MetricsCollectorImpl demonstrating real-world usage scenarios.
 */
class MetricsCollectorIntegrationTest {
    
    private MetricsCollectorImpl metricsCollector;
    
    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollectorImpl();
    }
    
    @Test
    @DisplayName("Simulate cache operations and verify metrics")
    void testCacheOperationSimulation() throws InterruptedException {
        // Simulate cache operations over time
        // Initial burst of operations
        for (int i = 0; i < 50; i++) {
            metricsCollector.recordHit();
            metricsCollector.recordGetLatency(5);
        }
        
        for (int i = 0; i < 10; i++) {
            metricsCollector.recordMiss();
            metricsCollector.recordGetLatency(8);
        }
        
        // Update memory usage
        metricsCollector.recordMemoryUsage(500_000_000, 1_000_000_000); // 500MB / 1GB
        
        // Get metrics snapshot
        CacheMetrics metrics1 = metricsCollector.getMetrics();
        
        // Verify initial metrics
        assertEquals(50, metrics1.getTotalHits());
        assertEquals(10, metrics1.getTotalMisses());
        assertEquals(50.0, metrics1.getMemoryUsagePercentage(), 0.1);
        assertTrue(metrics1.getAverageGetLatencyMs() > 0);
        
        // Wait a bit and simulate more operations
        Thread.sleep(500);
        
        for (int i = 0; i < 30; i++) {
            metricsCollector.recordHit();
            metricsCollector.recordGetLatency(6);
        }
        
        for (int i = 0; i < 5; i++) {
            metricsCollector.recordMiss();
            metricsCollector.recordGetLatency(10);
        }
        
        // Update memory usage
        metricsCollector.recordMemoryUsage(750_000_000, 1_000_000_000); // 750MB / 1GB
        
        // Get updated metrics
        CacheMetrics metrics2 = metricsCollector.getMetrics();
        
        // Verify updated metrics
        assertEquals(80, metrics2.getTotalHits());
        assertEquals(15, metrics2.getTotalMisses());
        assertEquals(75.0, metrics2.getMemoryUsagePercentage(), 0.1);
        
        // Verify per-second rates are calculated
        assertTrue(metrics2.getHitsPerSecond() > 0);
        assertTrue(metrics2.getMissesPerSecond() > 0);
    }
    
    @Test
    @DisplayName("Verify metrics reset functionality")
    void testMetricsResetInRealScenario() throws InterruptedException {
        // Simulate some operations
        for (int i = 0; i < 100; i++) {
            metricsCollector.recordHit();
            metricsCollector.recordGetLatency(5);
        }
        
        metricsCollector.recordMemoryUsage(500_000_000, 1_000_000_000);
        
        CacheMetrics beforeReset = metricsCollector.getMetrics();
        assertEquals(100, beforeReset.getTotalHits());
        
        // Reset metrics
        metricsCollector.reset();
        
        CacheMetrics afterReset = metricsCollector.getMetrics();
        assertEquals(0, afterReset.getTotalHits());
        assertEquals(0, afterReset.getTotalMisses());
        assertEquals(0.0, afterReset.getAverageGetLatencyMs(), 0.001);
        assertEquals(0.0, afterReset.getMemoryUsagePercentage(), 0.001);
    }
    
    @Test
    @DisplayName("Verify hit rate calculation over time")
    void testHitRateCalculation() throws InterruptedException {
        // Simulate 100 hits over 1 second
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 100; i++) {
            metricsCollector.recordHit();
            Thread.sleep(10); // 10ms between each hit
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        
        // Verify total hits
        assertEquals(100, metrics.getTotalHits());
        
        // Verify hits per second is reasonable
        // Should be around 100 hits / (duration in seconds)
        double expectedRate = 100.0 / (duration / 1000.0);
        assertTrue(metrics.getHitsPerSecond() > 0);
        assertTrue(metrics.getHitsPerSecond() <= expectedRate * 1.5); // Allow 50% tolerance
    }
    
    @Test
    @DisplayName("Verify average latency calculation with varying latencies")
    void testAverageLatencyWithVariation() {
        // Record latencies with different values
        metricsCollector.recordGetLatency(5);
        metricsCollector.recordGetLatency(10);
        metricsCollector.recordGetLatency(15);
        metricsCollector.recordGetLatency(20);
        metricsCollector.recordGetLatency(25);
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        
        // Average should be (5+10+15+20+25)/5 = 15
        assertEquals(15.0, metrics.getAverageGetLatencyMs(), 0.001);
    }
    
    @Test
    @DisplayName("Verify memory usage tracking with capacity changes")
    void testMemoryUsageTracking() {
        // Start with 25% usage
        metricsCollector.recordMemoryUsage(250_000_000, 1_000_000_000);
        CacheMetrics metrics1 = metricsCollector.getMetrics();
        assertEquals(25.0, metrics1.getMemoryUsagePercentage(), 0.1);
        
        // Increase to 50% usage
        metricsCollector.recordMemoryUsage(500_000_000, 1_000_000_000);
        CacheMetrics metrics2 = metricsCollector.getMetrics();
        assertEquals(50.0, metrics2.getMemoryUsagePercentage(), 0.1);
        
        // Increase to 95% usage (near eviction threshold)
        metricsCollector.recordMemoryUsage(950_000_000, 1_000_000_000);
        CacheMetrics metrics3 = metricsCollector.getMetrics();
        assertEquals(95.0, metrics3.getMemoryUsagePercentage(), 0.1);
    }
    
    @Test
    @DisplayName("Verify metrics under high load")
    void testHighLoadScenario() throws InterruptedException {
        // Simulate high load with rapid operations
        for (int i = 0; i < 1000; i++) {
            if (i % 3 == 0) {
                metricsCollector.recordHit();
            } else {
                metricsCollector.recordMiss();
            }
            metricsCollector.recordGetLatency(i % 20);
        }
        
        metricsCollector.recordMemoryUsage(800_000_000, 1_000_000_000);
        
        CacheMetrics metrics = metricsCollector.getMetrics();
        
        // Verify counts
        assertTrue(metrics.getTotalHits() > 300); // Approximately 1/3 of 1000
        assertTrue(metrics.getTotalMisses() > 600); // Approximately 2/3 of 1000
        assertEquals(1000, metrics.getTotalHits() + metrics.getTotalMisses());
        
        // Verify other metrics are calculated
        assertTrue(metrics.getAverageGetLatencyMs() >= 0);
        assertEquals(80.0, metrics.getMemoryUsagePercentage(), 0.1);
    }
}
