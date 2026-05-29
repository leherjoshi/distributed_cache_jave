package com.distributedcache.node;

/**
 * Snapshot of cache node metrics.
 */
public class NodeMetrics {
    private final double hitsPerSecond;
    private final double missesPerSecond;
    private final double averageGetLatencyMs;
    private final double memoryUsagePercentage;
    private final long totalHits;
    private final long totalMisses;
    
    public NodeMetrics(double hitsPerSecond, double missesPerSecond, 
                      double averageGetLatencyMs, double memoryUsagePercentage,
                      long totalHits, long totalMisses) {
        this.hitsPerSecond = hitsPerSecond;
        this.missesPerSecond = missesPerSecond;
        this.averageGetLatencyMs = averageGetLatencyMs;
        this.memoryUsagePercentage = memoryUsagePercentage;
        this.totalHits = totalHits;
        this.totalMisses = totalMisses;
    }
    
    public double getHitsPerSecond() {
        return hitsPerSecond;
    }
    
    public double getMissesPerSecond() {
        return missesPerSecond;
    }
    
    public double getAverageGetLatencyMs() {
        return averageGetLatencyMs;
    }
    
    public double getMemoryUsagePercentage() {
        return memoryUsagePercentage;
    }
    
    public long getTotalHits() {
        return totalHits;
    }
    
    public long getTotalMisses() {
        return totalMisses;
    }
    
    @Override
    public String toString() {
        return "NodeMetrics{" +
                "hitsPerSecond=" + hitsPerSecond +
                ", missesPerSecond=" + missesPerSecond +
                ", averageGetLatencyMs=" + averageGetLatencyMs +
                ", memoryUsagePercentage=" + memoryUsagePercentage +
                ", totalHits=" + totalHits +
                ", totalMisses=" + totalMisses +
                '}';
    }
}
