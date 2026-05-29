package com.distributedcache.utils;

import com.distributedcache.eviction.EvictionPolicyType;
import com.distributedcache.exceptions.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Implementation of ConfigurationManager using Java Properties format.
 * Handles loading, saving, validating, and pretty-printing cache configurations.
 */
public class PropertiesConfigurationManager implements ConfigurationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(PropertiesConfigurationManager.class);
    
    // Property keys
    private static final String KEY_CACHE_CAPACITY = "cache.capacity.bytes";
    private static final String KEY_REPLICATION_FACTOR = "replication.factor";
    private static final String KEY_EVICTION_POLICY = "eviction.policy";
    private static final String KEY_HEALTH_CHECK_INTERVAL = "health.check.interval.seconds";
    private static final String KEY_SERVER_PORT = "server.port";
    private static final String KEY_SEED_NODES = "seed.nodes";
    
    @Override
    public CacheConfiguration load(Path configFile) throws ConfigurationException {
        if (configFile == null) {
            throw new ConfigurationException("Configuration file path cannot be null");
        }
        
        if (!Files.exists(configFile)) {
            throw new ConfigurationException("Configuration file does not exist: " + configFile);
        }
        
        if (!Files.isRegularFile(configFile)) {
            throw new ConfigurationException("Configuration path is not a regular file: " + configFile);
        }
        
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configFile)) {
            properties.load(input);
            logger.info("Loaded configuration from: {}", configFile);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read configuration file: " + configFile, e);
        }
        
        return parseProperties(properties);
    }
    
    @Override
    public void save(CacheConfiguration config, Path configFile) throws ConfigurationException {
        if (config == null) {
            throw new ConfigurationException("Configuration cannot be null");
        }
        
        if (configFile == null) {
            throw new ConfigurationException("Configuration file path cannot be null");
        }
        
        // Validate before saving
        ValidationResult validationResult = validate(config);
        if (!validationResult.isValid()) {
            throw new ConfigurationException("Cannot save invalid configuration: " + String.join(", ", validationResult.getErrors()));
        }
        
        Properties properties = toProperties(config);
        
        // Create parent directories if they don't exist
        Path parentDir = configFile.getParent();
        if (parentDir != null) {
            try {
                Files.createDirectories(parentDir);
            } catch (IOException e) {
                throw new ConfigurationException("Failed to create parent directories for: " + configFile, e);
            }
        }
        
        try (OutputStream output = Files.newOutputStream(configFile)) {
            properties.store(output, "Distributed Cache System Configuration");
            logger.info("Saved configuration to: {}", configFile);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write configuration file: " + configFile, e);
        }
    }
    
    @Override
    public ValidationResult validate(CacheConfiguration config) {
        if (config == null) {
            return ValidationResult.failure("Configuration cannot be null");
        }
        
        List<String> errors = new ArrayList<>();
        
        // Validate cache capacity
        long capacity = config.getCacheCapacityBytes();
        if (capacity < CacheConfiguration.MIN_CAPACITY) {
            errors.add(String.format("Cache capacity (%d bytes) is below minimum (%d bytes)", 
                capacity, CacheConfiguration.MIN_CAPACITY));
        }
        if (capacity > CacheConfiguration.MAX_CAPACITY) {
            errors.add(String.format("Cache capacity (%d bytes) exceeds maximum (%d bytes)", 
                capacity, CacheConfiguration.MAX_CAPACITY));
        }
        
        // Validate replication factor
        int replicationFactor = config.getReplicationFactor();
        if (replicationFactor < 1) {
            errors.add(String.format("Replication factor (%d) must be at least 1", replicationFactor));
        }
        if (replicationFactor > 5) {
            errors.add(String.format("Replication factor (%d) cannot exceed 5", replicationFactor));
        }
        
        // Validate eviction policy
        if (config.getEvictionPolicy() == null) {
            errors.add("Eviction policy cannot be null");
        }
        
        // Validate health check interval
        Duration healthCheckInterval = config.getHealthCheckInterval();
        if (healthCheckInterval == null) {
            errors.add("Health check interval cannot be null");
        } else {
            if (healthCheckInterval.compareTo(CacheConfiguration.MIN_HEALTH_CHECK) < 0) {
                errors.add(String.format("Health check interval (%s) is below minimum (%s)", 
                    healthCheckInterval, CacheConfiguration.MIN_HEALTH_CHECK));
            }
            if (healthCheckInterval.compareTo(CacheConfiguration.MAX_HEALTH_CHECK) > 0) {
                errors.add(String.format("Health check interval (%s) exceeds maximum (%s)", 
                    healthCheckInterval, CacheConfiguration.MAX_HEALTH_CHECK));
            }
        }
        
        // Validate server port
        int serverPort = config.getServerPort();
        if (serverPort < 1 || serverPort > 65535) {
            errors.add(String.format("Server port (%d) must be between 1 and 65535", serverPort));
        }
        
        // Validate seed nodes (can be empty, but if present should not contain null or empty strings)
        List<String> seedNodes = config.getSeedNodes();
        if (seedNodes != null) {
            for (int i = 0; i < seedNodes.size(); i++) {
                String node = seedNodes.get(i);
                if (node == null || node.trim().isEmpty()) {
                    errors.add(String.format("Seed node at index %d is null or empty", i));
                }
            }
        }
        
        if (errors.isEmpty()) {
            return ValidationResult.success();
        } else {
            return ValidationResult.failure(errors);
        }
    }
    
    @Override
    public String prettyPrint(CacheConfiguration config) {
        if (config == null) {
            return "null";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Distributed Cache Configuration\n");
        sb.append("================================\n\n");
        
        sb.append("Cache Settings:\n");
        sb.append(String.format("  Capacity: %s\n", formatBytes(config.getCacheCapacityBytes())));
        sb.append(String.format("  Eviction Policy: %s\n", config.getEvictionPolicy()));
        sb.append("\n");
        
        sb.append("Replication Settings:\n");
        sb.append(String.format("  Replication Factor: %d\n", config.getReplicationFactor()));
        sb.append("\n");
        
        sb.append("Network Settings:\n");
        sb.append(String.format("  Server Port: %d\n", config.getServerPort()));
        sb.append(String.format("  Seed Nodes: %s\n", 
            config.getSeedNodes().isEmpty() ? "none" : String.join(", ", config.getSeedNodes())));
        sb.append("\n");
        
        sb.append("Health Monitoring:\n");
        sb.append(String.format("  Check Interval: %d seconds\n", 
            config.getHealthCheckInterval() != null ? config.getHealthCheckInterval().getSeconds() : 0));
        
        return sb.toString();
    }
    
    /**
     * Parses a Properties object into a CacheConfiguration.
     */
    private CacheConfiguration parseProperties(Properties properties) throws ConfigurationException {
        CacheConfiguration config = new CacheConfiguration();
        
        try {
            // Parse cache capacity
            String capacityStr = properties.getProperty(KEY_CACHE_CAPACITY);
            if (capacityStr == null) {
                throw new ConfigurationException("Missing required property: " + KEY_CACHE_CAPACITY);
            }
            config.setCacheCapacityBytes(Long.parseLong(capacityStr));
            
            // Parse replication factor
            String replicationStr = properties.getProperty(KEY_REPLICATION_FACTOR);
            if (replicationStr == null) {
                throw new ConfigurationException("Missing required property: " + KEY_REPLICATION_FACTOR);
            }
            config.setReplicationFactor(Integer.parseInt(replicationStr));
            
            // Parse eviction policy
            String evictionStr = properties.getProperty(KEY_EVICTION_POLICY);
            if (evictionStr == null) {
                throw new ConfigurationException("Missing required property: " + KEY_EVICTION_POLICY);
            }
            config.setEvictionPolicy(EvictionPolicyType.valueOf(evictionStr.toUpperCase()));
            
            // Parse health check interval
            String healthCheckStr = properties.getProperty(KEY_HEALTH_CHECK_INTERVAL);
            if (healthCheckStr == null) {
                throw new ConfigurationException("Missing required property: " + KEY_HEALTH_CHECK_INTERVAL);
            }
            config.setHealthCheckInterval(Duration.ofSeconds(Long.parseLong(healthCheckStr)));
            
            // Parse server port
            String portStr = properties.getProperty(KEY_SERVER_PORT);
            if (portStr == null) {
                throw new ConfigurationException("Missing required property: " + KEY_SERVER_PORT);
            }
            config.setServerPort(Integer.parseInt(portStr));
            
            // Parse seed nodes (optional)
            String seedNodesStr = properties.getProperty(KEY_SEED_NODES, "");
            List<String> seedNodes = Arrays.stream(seedNodesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
            config.setSeedNodes(seedNodes);
            
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid number format in configuration", e);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid configuration value", e);
        }
        
        return config;
    }
    
    /**
     * Converts a CacheConfiguration to a Properties object.
     */
    private Properties toProperties(CacheConfiguration config) {
        Properties properties = new Properties();
        
        properties.setProperty(KEY_CACHE_CAPACITY, String.valueOf(config.getCacheCapacityBytes()));
        properties.setProperty(KEY_REPLICATION_FACTOR, String.valueOf(config.getReplicationFactor()));
        properties.setProperty(KEY_EVICTION_POLICY, config.getEvictionPolicy().name());
        properties.setProperty(KEY_HEALTH_CHECK_INTERVAL, 
            String.valueOf(config.getHealthCheckInterval().getSeconds()));
        properties.setProperty(KEY_SERVER_PORT, String.valueOf(config.getServerPort()));
        
        if (!config.getSeedNodes().isEmpty()) {
            properties.setProperty(KEY_SEED_NODES, String.join(",", config.getSeedNodes()));
        } else {
            properties.setProperty(KEY_SEED_NODES, "");
        }
        
        return properties;
    }
    
    /**
     * Formats bytes into a human-readable string.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
