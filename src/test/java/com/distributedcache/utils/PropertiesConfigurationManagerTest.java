package com.distributedcache.utils;

import com.distributedcache.eviction.EvictionPolicyType;
import com.distributedcache.exceptions.ConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PropertiesConfigurationManager.
 */
class PropertiesConfigurationManagerTest {
    
    private PropertiesConfigurationManager configManager;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        configManager = new PropertiesConfigurationManager();
    }
    
    @Test
    void testSaveAndLoadValidConfiguration() throws ConfigurationException {
        // Create a valid configuration
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024, // 10 MB
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Arrays.asList("node1:8080", "node2:8080")
        );
        
        Path configFile = tempDir.resolve("cache.properties");
        
        // Save configuration
        configManager.save(config, configFile);
        
        // Verify file was created
        assertTrue(Files.exists(configFile));
        
        // Load configuration
        CacheConfiguration loadedConfig = configManager.load(configFile);
        
        // Verify all fields match
        assertEquals(config.getCacheCapacityBytes(), loadedConfig.getCacheCapacityBytes());
        assertEquals(config.getReplicationFactor(), loadedConfig.getReplicationFactor());
        assertEquals(config.getEvictionPolicy(), loadedConfig.getEvictionPolicy());
        assertEquals(config.getHealthCheckInterval(), loadedConfig.getHealthCheckInterval());
        assertEquals(config.getServerPort(), loadedConfig.getServerPort());
        assertEquals(config.getSeedNodes(), loadedConfig.getSeedNodes());
    }
    
    @Test
    void testSaveAndLoadConfigurationWithEmptySeedNodes() throws ConfigurationException {
        CacheConfiguration config = new CacheConfiguration(
            5 * 1024 * 1024,
            1,
            EvictionPolicyType.FIFO,
            Duration.ofSeconds(10),
            9000,
            Collections.emptyList()
        );
        
        Path configFile = tempDir.resolve("cache-no-seeds.properties");
        
        configManager.save(config, configFile);
        CacheConfiguration loadedConfig = configManager.load(configFile);
        
        assertTrue(loadedConfig.getSeedNodes().isEmpty());
    }
    
    @Test
    void testLoadNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.properties");
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.load(nonExistentFile);
        });
        
        assertTrue(exception.getMessage().contains("does not exist"));
    }
    
    @Test
    void testLoadNullPath() {
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.load(null);
        });
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }
    
    @Test
    void testLoadDirectory() throws IOException {
        Path directory = tempDir.resolve("directory");
        Files.createDirectory(directory);
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.load(directory);
        });
        
        assertTrue(exception.getMessage().contains("not a regular file"));
    }
    
    @Test
    void testLoadInvalidPropertiesFile() throws IOException {
        Path configFile = tempDir.resolve("invalid.properties");
        Files.writeString(configFile, "cache.capacity.bytes=not_a_number\n");
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.load(configFile);
        });
        
        assertTrue(exception.getMessage().contains("Invalid number format") || 
                   exception.getMessage().contains("number"));
    }
    
    @Test
    void testLoadMissingRequiredProperty() throws IOException {
        Path configFile = tempDir.resolve("incomplete.properties");
        Files.writeString(configFile, "cache.capacity.bytes=1048576\n");
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.load(configFile);
        });
        
        assertTrue(exception.getMessage().contains("Missing required property"));
    }
    
    @Test
    void testSaveNullConfiguration() {
        Path configFile = tempDir.resolve("cache.properties");
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.save(null, configFile);
        });
        
        assertTrue(exception.getMessage().contains("Configuration cannot be null"));
    }
    
    @Test
    void testSaveNullPath() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Collections.emptyList()
        );
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.save(config, null);
        });
        
        assertTrue(exception.getMessage().contains("path cannot be null"));
    }
    
    @Test
    void testSaveInvalidConfiguration() {
        // Create configuration with invalid capacity (below minimum)
        CacheConfiguration config = new CacheConfiguration(
            100, // Too small
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Collections.emptyList()
        );
        
        Path configFile = tempDir.resolve("cache.properties");
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.save(config, configFile);
        });
        
        assertTrue(exception.getMessage().contains("invalid configuration"));
    }
    
    @Test
    void testValidateValidConfiguration() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Arrays.asList("node1:8080")
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }
    
    @Test
    void testValidateNullConfiguration() {
        ValidationResult result = configManager.validate(null);
        
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("cannot be null"));
    }
    
    @Test
    void testValidateCapacityBelowMinimum() {
        CacheConfiguration config = new CacheConfiguration(
            100, // Below 1 MB minimum
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("below minimum")));
    }
    
    @Test
    void testValidateCapacityAboveMaximum() {
        CacheConfiguration config = new CacheConfiguration(
            200L * 1024 * 1024 * 1024, // Above 100 GB maximum
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("exceeds maximum")));
    }
    
    @Test
    void testValidateReplicationFactorBelowMinimum() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            0, // Below minimum of 1
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("must be at least 1")));
    }
    
    @Test
    void testValidateReplicationFactorAboveMaximum() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            6, // Above maximum of 5
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("cannot exceed 5")));
    }
    
    @Test
    void testValidateNullEvictionPolicy() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            null, // Null eviction policy
            Duration.ofSeconds(5),
            8080,
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("Eviction policy cannot be null")));
    }
    
    @Test
    void testValidateNullHealthCheckInterval() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            null, // Null health check interval
            8080,
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("Health check interval cannot be null")));
    }
    
    @Test
    void testValidateHealthCheckIntervalBelowMinimum() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            Duration.ofMillis(500), // Below 1 second minimum
            8080,
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("below minimum")));
    }
    
    @Test
    void testValidateHealthCheckIntervalAboveMaximum() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(120), // Above 60 second maximum
            8080,
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("exceeds maximum")));
    }
    
    @Test
    void testValidateInvalidServerPort() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            70000, // Above valid port range
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("must be between 1 and 65535")));
    }
    
    @Test
    void testValidateEmptySeedNode() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Arrays.asList("node1:8080", "", "node2:8080")
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(e -> e.contains("null or empty")));
    }
    
    @Test
    void testValidateMultipleErrors() {
        CacheConfiguration config = new CacheConfiguration(
            100, // Too small
            0, // Too small
            null, // Null
            Duration.ofMillis(100), // Too small
            70000, // Invalid port
            Collections.emptyList()
        );
        
        ValidationResult result = configManager.validate(config);
        
        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() >= 5);
    }
    
    @Test
    void testPrettyPrintValidConfiguration() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Arrays.asList("node1:8080", "node2:8080")
        );
        
        String output = configManager.prettyPrint(config);
        
        assertNotNull(output);
        assertTrue(output.contains("Distributed Cache Configuration"));
        assertTrue(output.contains("Capacity:"));
        assertTrue(output.contains("Eviction Policy: LRU"));
        assertTrue(output.contains("Replication Factor: 3"));
        assertTrue(output.contains("Server Port: 8080"));
        assertTrue(output.contains("node1:8080"));
        assertTrue(output.contains("Check Interval: 5 seconds"));
    }
    
    @Test
    void testPrettyPrintNullConfiguration() {
        String output = configManager.prettyPrint(null);
        
        assertEquals("null", output);
    }
    
    @Test
    void testPrettyPrintConfigurationWithNoSeedNodes() {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            1,
            EvictionPolicyType.FIFO,
            Duration.ofSeconds(10),
            9000,
            Collections.emptyList()
        );
        
        String output = configManager.prettyPrint(config);
        
        assertTrue(output.contains("Seed Nodes: none"));
    }
    
    @Test
    void testRoundTripPreservesConfiguration() throws ConfigurationException {
        // Create original configuration
        CacheConfiguration original = new CacheConfiguration(
            50 * 1024 * 1024,
            2,
            EvictionPolicyType.LFU,
            Duration.ofSeconds(15),
            8888,
            Arrays.asList("host1:8080", "host2:8080", "host3:8080")
        );
        
        Path configFile = tempDir.resolve("roundtrip.properties");
        
        // Save and load
        configManager.save(original, configFile);
        CacheConfiguration loaded = configManager.load(configFile);
        
        // Verify all fields are preserved
        assertEquals(original.getCacheCapacityBytes(), loaded.getCacheCapacityBytes());
        assertEquals(original.getReplicationFactor(), loaded.getReplicationFactor());
        assertEquals(original.getEvictionPolicy(), loaded.getEvictionPolicy());
        assertEquals(original.getHealthCheckInterval(), loaded.getHealthCheckInterval());
        assertEquals(original.getServerPort(), loaded.getServerPort());
        assertEquals(original.getSeedNodes(), loaded.getSeedNodes());
    }
    
    @Test
    void testSaveCreatesParentDirectories() throws ConfigurationException {
        CacheConfiguration config = new CacheConfiguration(
            10 * 1024 * 1024,
            3,
            EvictionPolicyType.LRU,
            Duration.ofSeconds(5),
            8080,
            Collections.emptyList()
        );
        
        Path configFile = tempDir.resolve("subdir1/subdir2/cache.properties");
        
        configManager.save(config, configFile);
        
        assertTrue(Files.exists(configFile));
        assertTrue(Files.isDirectory(configFile.getParent()));
    }
    
    @Test
    void testLoadInvalidEvictionPolicy() throws IOException {
        Path configFile = tempDir.resolve("invalid-eviction.properties");
        Files.writeString(configFile, 
            "cache.capacity.bytes=10485760\n" +
            "replication.factor=3\n" +
            "eviction.policy=INVALID_POLICY\n" +
            "health.check.interval.seconds=5\n" +
            "server.port=8080\n" +
            "seed.nodes=\n"
        );
        
        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> {
            configManager.load(configFile);
        });
        
        assertTrue(exception.getMessage().contains("Invalid configuration value"));
    }
    
    @Test
    void testValidateBoundaryValues() {
        // Test minimum valid values
        CacheConfiguration minConfig = new CacheConfiguration(
            CacheConfiguration.MIN_CAPACITY,
            1,
            EvictionPolicyType.LRU,
            CacheConfiguration.MIN_HEALTH_CHECK,
            1,
            Collections.emptyList()
        );
        
        ValidationResult minResult = configManager.validate(minConfig);
        assertTrue(minResult.isValid());
        
        // Test maximum valid values
        CacheConfiguration maxConfig = new CacheConfiguration(
            CacheConfiguration.MAX_CAPACITY,
            5,
            EvictionPolicyType.LRU,
            CacheConfiguration.MAX_HEALTH_CHECK,
            65535,
            Collections.emptyList()
        );
        
        ValidationResult maxResult = configManager.validate(maxConfig);
        assertTrue(maxResult.isValid());
    }
}
