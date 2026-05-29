package com.distributedcache.utils;

import com.distributedcache.exceptions.ConfigurationException;
import java.nio.file.Path;

/**
 * Manages system configuration.
 */
public interface ConfigurationManager {
    
    /**
     * Loads configuration from a file.
     */
    CacheConfiguration load(Path configFile) throws ConfigurationException;
    
    /**
     * Saves configuration to a file.
     */
    void save(CacheConfiguration config, Path configFile) throws ConfigurationException;
    
    /**
     * Validates a configuration object.
     */
    ValidationResult validate(CacheConfiguration config);
    
    /**
     * Formats a configuration object as a human-readable string.
     */
    String prettyPrint(CacheConfiguration config);
}
