package com.distributedcache.generators;

import com.distributedcache.eviction.EvictionPolicyType;
import com.distributedcache.hashing.NodeInfo;
import com.distributedcache.utils.CacheConfiguration;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

/**
 * Shared jqwik generators for property-based testing.
 * Provides arbitraries for common cache system types.
 */
public class CacheArbitraries {
    
    /**
     * Generates valid cache keys (1-256 bytes).
     */
    public static Arbitrary<String> cacheKeys() {
        return Arbitraries.strings()
                .ascii()
                .ofMinLength(1)
                .ofMaxLength(256);
    }
    
    /**
     * Generates valid cache values as byte arrays (0-1 MB).
     */
    public static Arbitrary<byte[]> cacheValues() {
        return Arbitraries.bytes()
                .array(byte[].class)
                .ofMinSize(0)
                .ofMaxSize(1024 * 1024);  // 1 MB
    }
    
    /**
     * Generates valid cache values as strings (for simpler tests).
     */
    public static Arbitrary<String> cacheStringValues() {
        return Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(1000);
    }
    
    /**
     * Generates valid node IDs.
     */
    public static Arbitrary<String> nodeIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(4)
                .ofMaxLength(16);
    }
    
    /**
     * Generates valid IP addresses.
     */
    public static Arbitrary<String> ipAddresses() {
        return Combinators.combine(
                Arbitraries.integers().between(1, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(0, 255),
                Arbitraries.integers().between(1, 255)
        ).as((a, b, c, d) -> a + "." + b + "." + c + "." + d);
    }
    
    /**
     * Generates valid port numbers.
     */
    public static Arbitrary<Integer> ports() {
        return Arbitraries.integers().between(1024, 65535);
    }
    
    /**
     * Generates NodeInfo instances.
     */
    public static Arbitrary<NodeInfo> cacheNodes() {
        return Combinators.combine(
                nodeIds(),
                ipAddresses(),
                ports()
        ).as(NodeInfo::new);
    }
    
    /**
     * Generates valid replication factors (1-5).
     */
    public static Arbitrary<Integer> replicationFactors() {
        return Arbitraries.integers().between(1, 5);
    }
    
    /**
     * Generates eviction policy types.
     */
    public static Arbitrary<EvictionPolicyType> evictionPolicyTypes() {
        return Arbitraries.of(EvictionPolicyType.values());
    }
    
    /**
     * Generates valid cache capacities (1 MB to 100 GB).
     */
    public static Arbitrary<Long> cacheCapacities() {
        return Arbitraries.longs()
                .between(1024L * 1024L, 100L * 1024L * 1024L * 1024L);
    }
    
    /**
     * Generates valid health check intervals (1-60 seconds).
     */
    public static Arbitrary<Duration> healthCheckIntervals() {
        return Arbitraries.integers()
                .between(1, 60)
                .map(Duration::ofSeconds);
    }
    
    /**
     * Generates valid CacheConfiguration instances.
     */
    public static Arbitrary<CacheConfiguration> validConfigurations() {
        return Combinators.combine(
                cacheCapacities(),
                replicationFactors(),
                evictionPolicyTypes(),
                healthCheckIntervals(),
                ports(),
                Arbitraries.defaultFor(List.class, String.class)
        ).as((capacity, replication, eviction, healthCheck, port, seedNodes) -> 
                new CacheConfiguration(capacity, replication, eviction, healthCheck, port, 
                        seedNodes != null ? (List<String>) seedNodes : List.of()));
    }
    
    /**
     * Generates simple serializable objects for testing.
     */
    public static Arbitrary<Serializable> serializableObjects() {
        return Arbitraries.oneOf(
                Arbitraries.strings().map(s -> (Serializable) s),
                Arbitraries.integers().map(i -> (Serializable) i),
                Arbitraries.longs().map(l -> (Serializable) l),
                Arbitraries.doubles().map(d -> (Serializable) d),
                cacheValues().map(b -> (Serializable) b)
        );
    }
}
