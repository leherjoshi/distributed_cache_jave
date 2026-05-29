package com.distributedcache.utils;

import com.distributedcache.eviction.EvictionPolicyType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cache configuration object.
 */
public class CacheConfiguration {
    // Validation constraints
    public static final long MIN_CAPACITY = 1024 * 1024; // 1 MB
    public static final long MAX_CAPACITY = 100L * 1024 * 1024 * 1024; // 100 GB
    public static final Duration MIN_HEALTH_CHECK = Duration.ofSeconds(1);
    public static final Duration MAX_HEALTH_CHECK = Duration.ofSeconds(60);
    
    private long cacheCapacityBytes;
    private int replicationFactor;
    private EvictionPolicyType evictionPolicy;
    private Duration healthCheckInterval;
    private int serverPort;
    private List<String> seedNodes;
    
    public CacheConfiguration() {
        this.seedNodes = new ArrayList<>();
    }
    
    public CacheConfiguration(long cacheCapacityBytes, int replicationFactor, 
                            EvictionPolicyType evictionPolicy, Duration healthCheckInterval,
                            int serverPort, List<String> seedNodes) {
        this.cacheCapacityBytes = cacheCapacityBytes;
        this.replicationFactor = replicationFactor;
        this.evictionPolicy = evictionPolicy;
        this.healthCheckInterval = healthCheckInterval;
        this.serverPort = serverPort;
        this.seedNodes = seedNodes != null ? new ArrayList<>(seedNodes) : new ArrayList<>();
    }
    
    public long getCacheCapacityBytes() {
        return cacheCapacityBytes;
    }
    
    public void setCacheCapacityBytes(long cacheCapacityBytes) {
        this.cacheCapacityBytes = cacheCapacityBytes;
    }
    
    public int getReplicationFactor() {
        return replicationFactor;
    }
    
    public void setReplicationFactor(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }
    
    public EvictionPolicyType getEvictionPolicy() {
        return evictionPolicy;
    }
    
    public void setEvictionPolicy(EvictionPolicyType evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }
    
    public Duration getHealthCheckInterval() {
        return healthCheckInterval;
    }
    
    public void setHealthCheckInterval(Duration healthCheckInterval) {
        this.healthCheckInterval = healthCheckInterval;
    }
    
    public int getServerPort() {
        return serverPort;
    }
    
    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }
    
    public List<String> getSeedNodes() {
        return new ArrayList<>(seedNodes);
    }
    
    public void setSeedNodes(List<String> seedNodes) {
        this.seedNodes = seedNodes != null ? new ArrayList<>(seedNodes) : new ArrayList<>();
    }
    
    @Override
    public String toString() {
        return "CacheConfiguration{" +
                "cacheCapacityBytes=" + cacheCapacityBytes +
                ", replicationFactor=" + replicationFactor +
                ", evictionPolicy=" + evictionPolicy +
                ", healthCheckInterval=" + healthCheckInterval +
                ", serverPort=" + serverPort +
                ", seedNodes=" + seedNodes +
                '}';
    }
}
