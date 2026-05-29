package com.distributedcache.node;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Implementation of MetricsCollector using atomic counters for thread-safe metrics collection.
 * Uses AtomicLong for hit/miss counters and LongAdder for latency accumulation.
 * Calculates hits/misses per second using a sliding window approach.
 */
public class MetricsCollectorImpl implements MetricsCollector {
    
    // Atomic counters for thread-safe operations
    private final AtomicLong totalHits;
    private final AtomicLong totalMisses;
    private final LongAdder latencyAccumulator;
    private final AtomicLong latencyCount;
    
    // Memory usage tracking
    private volatile long currentMemoryBytes;
    private volatile long totalCapacityBytes;
    
    // Sliding window for per-second calculations
    private final Queue<MetricsSnapshot> slidingWindow;
    private final ReadWriteLock windowLock;
    private volatile long lastUpdateTime;
    
    // Window size in milliseconds (1 second)
    private static final long WINDOW_SIZE_MS = 1000;
    
    /**
     * Internal snapshot for sliding window calculations.
     */
    private static class MetricsSnapshot {
        final long timestamp;
        final long hits;
        final long misses;
        
        MetricsSnapshot(long timestamp, long hits, long misses) {
            this.timestamp = timestamp;
            this.hits = hits;
            this.misses = misses;
        }
    }
    
    /**
     * Creates a new MetricsCollectorImpl with all counters initialized to zero.
     */
    public MetricsCollectorImpl() {
        this.totalHits = new AtomicLong(0);
        this.totalMisses = new AtomicLong(0);
        this.latencyAccumulator = new LongAdder();
        this.latencyCount = new AtomicLong(0);
        this.currentMemoryBytes = 0;
        this.totalCapacityBytes = 0;
        this.slidingWindow = new LinkedList<>();
        this.windowLock = new ReentrantReadWriteLock();
        this.lastUpdateTime = System.currentTimeMillis();
        
        // Initialize with a snapshot at creation time
        updateSlidingWindow();
    }
    
    @Override
    public void recordHit() {
        totalHits.incrementAndGet();
        updateSlidingWindowIfNeeded();
    }
    
    @Override
    public void recordMiss() {
        totalMisses.incrementAndGet();
        updateSlidingWindowIfNeeded();
    }
    
    @Override
    public void recordGetLatency(long latencyMs) {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("Latency cannot be negative: " + latencyMs);
        }
        latencyAccumulator.add(latencyMs);
        latencyCount.incrementAndGet();
    }
    
    @Override
    public void recordMemoryUsage(long bytes, long capacity) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Memory usage cannot be negative: " + bytes);
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        this.currentMemoryBytes = bytes;
        this.totalCapacityBytes = capacity;
    }
    
    @Override
    public CacheMetrics getMetrics() {
        updateSlidingWindowIfNeeded();
        
        long hits = totalHits.get();
        long misses = totalMisses.get();
        
        // Calculate per-second rates using sliding window
        double hitsPerSecond = calculateRate(hits, misses).hitsPerSecond;
        double missesPerSecond = calculateRate(hits, misses).missesPerSecond;
        
        // Calculate average latency
        double averageLatency = calculateAverageLatency();
        
        // Calculate memory usage percentage
        double memoryPercentage = calculateMemoryPercentage();
        
        return new CacheMetrics(
            hitsPerSecond,
            missesPerSecond,
            averageLatency,
            memoryPercentage,
            hits,
            misses
        );
    }
    
    @Override
    public void reset() {
        totalHits.set(0);
        totalMisses.set(0);
        latencyAccumulator.reset();
        latencyCount.set(0);
        currentMemoryBytes = 0;
        totalCapacityBytes = 0;
        
        windowLock.writeLock().lock();
        try {
            slidingWindow.clear();
            lastUpdateTime = System.currentTimeMillis();
            slidingWindow.offer(new MetricsSnapshot(lastUpdateTime, 0, 0));
        } finally {
            windowLock.writeLock().unlock();
        }
    }
    
    /**
     * Updates the sliding window if UPDATE_INTERVAL_MS has passed since last update.
     */
    private void updateSlidingWindowIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL_MS) {
            updateSlidingWindow();
        }
    }
    
    /**
     * Adds a new snapshot to the sliding window and removes old snapshots.
     */
    private void updateSlidingWindow() {
        long currentTime = System.currentTimeMillis();
        long hits = totalHits.get();
        long misses = totalMisses.get();
        
        windowLock.writeLock().lock();
        try {
            // Add current snapshot
            slidingWindow.offer(new MetricsSnapshot(currentTime, hits, misses));
            
            // Remove snapshots older than the window size
            long cutoffTime = currentTime - WINDOW_SIZE_MS;
            while (!slidingWindow.isEmpty() && slidingWindow.peek().timestamp < cutoffTime) {
                slidingWindow.poll();
            }
            
            lastUpdateTime = currentTime;
        } finally {
            windowLock.writeLock().unlock();
        }
    }
    
    /**
     * Calculates hits and misses per second using the sliding window.
     */
    private RateResult calculateRate(long currentHits, long currentMisses) {
        windowLock.readLock().lock();
        try {
            if (slidingWindow.isEmpty()) {
                return new RateResult(0.0, 0.0);
            }
            
            // If we only have one snapshot, calculate rate from that snapshot to now
            if (slidingWindow.size() == 1) {
                MetricsSnapshot snapshot = slidingWindow.peek();
                if (snapshot == null) {
                    return new RateResult(0.0, 0.0);
                }
                
                long currentTime = System.currentTimeMillis();
                long timeDeltaMs = currentTime - snapshot.timestamp;
                
                // If time delta is too small, return 0 to avoid division issues
                if (timeDeltaMs < 100) {
                    return new RateResult(0.0, 0.0);
                }
                
                long hitsDelta = currentHits - snapshot.hits;
                long missesDelta = currentMisses - snapshot.misses;
                
                // Convert to per-second rate
                double timeInSeconds = timeDeltaMs / 1000.0;
                double hitsPerSecond = hitsDelta / timeInSeconds;
                double missesPerSecond = missesDelta / timeInSeconds;
                
                return new RateResult(hitsPerSecond, missesPerSecond);
            }
            
            // Get the oldest snapshot in the window
            MetricsSnapshot oldest = slidingWindow.peek();
            if (oldest == null) {
                return new RateResult(0.0, 0.0);
            }
            
            long currentTime = System.currentTimeMillis();
            long timeDeltaMs = currentTime - oldest.timestamp;
            
            // If time delta is too small, return 0 to avoid division issues
            if (timeDeltaMs < 100) {
                return new RateResult(0.0, 0.0);
            }
            
            long hitsDelta = currentHits - oldest.hits;
            long missesDelta = currentMisses - oldest.misses;
            
            // Convert to per-second rate
            double timeInSeconds = timeDeltaMs / 1000.0;
            double hitsPerSecond = hitsDelta / timeInSeconds;
            double missesPerSecond = missesDelta / timeInSeconds;
            
            return new RateResult(hitsPerSecond, missesPerSecond);
        } finally {
            windowLock.readLock().unlock();
        }
    }
    
    /**
     * Helper class to return both rates from calculateRate.
     */
    private static class RateResult {
        final double hitsPerSecond;
        final double missesPerSecond;
        
        RateResult(double hitsPerSecond, double missesPerSecond) {
            this.hitsPerSecond = hitsPerSecond;
            this.missesPerSecond = missesPerSecond;
        }
    }
    
    /**
     * Calculates the average latency from accumulated values.
     */
    private double calculateAverageLatency() {
        long count = latencyCount.get();
        if (count == 0) {
            return 0.0;
        }
        long totalLatency = latencyAccumulator.sum();
        return (double) totalLatency / count;
    }
    
    /**
     * Calculates the memory usage percentage.
     */
    private double calculateMemoryPercentage() {
        if (totalCapacityBytes == 0) {
            return 0.0;
        }
        return (currentMemoryBytes * 100.0) / totalCapacityBytes;
    }
}
