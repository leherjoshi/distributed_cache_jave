package com.distributedcache.node;

/**
 * Collects and exposes cache performance metrics.
 */
public interface MetricsCollector {
    
    /**
     * Metrics update interval.
     */
    long UPDATE_INTERVAL_MS = 1000;
    
    /**
     * Records a cache hit.
     */
    void recordHit();
    
    /**
     * Records a cache miss.
     */
    void recordMiss();
    
    /**
     * Records a get operation latency.
     */
    void recordGetLatency(long latencyMs);
    
    /**
     * Records current memory usage.
     */
    void recordMemoryUsage(long bytes, long capacity);
    
    /**
     * Gets current metrics snapshot.
     */
    CacheMetrics getMetrics();
    
    /**
     * Resets all metrics.
     */
    void reset();
}
