package com.distributedcache.eviction;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for LFUEvictionPolicy.
 */
class LFUEvictionPolicyProperties {
    
    /**
     * Property 12: LFU Eviction Removes Least Frequently Used Entry
     * 
     * **Validates: Requirements 4.3**
     * 
     * For any cache with LFU eviction policy, when eviction is triggered,
     * the entry with the lowest access count SHALL be among the entries evicted.
     */
    @Property
    void lfuEvictionRemovesLeastFrequentlyUsedEntry(
            @ForAll @IntRange(min = 2, max = 20) int numEntries) {
        
        LFUEvictionPolicy<String, String> lfuPolicy = new LFUEvictionPolicy<>();
        
        // Track the minimum access count
        int minAccessCount = Integer.MAX_VALUE;
        Set<String> keysWithMinAccess = new HashSet<>();
        
        // Add entries with varying access counts
        for (int i = 0; i < numEntries; i++) {
            String key = "key_" + i;
            int accessCount = i % 10; // Vary access counts: 0, 1, 2, ..., 9, 0, 1, ...
            
            // Put the entry
            lfuPolicy.onPut(key, "value" + i);
            
            // Simulate accesses
            for (int j = 0; j < accessCount; j++) {
                lfuPolicy.onAccess(key);
            }
            
            // Track minimum access count
            if (accessCount < minAccessCount) {
                minAccessCount = accessCount;
                keysWithMinAccess.clear();
                keysWithMinAccess.add(key);
            } else if (accessCount == minAccessCount) {
                keysWithMinAccess.add(key);
            }
        }
        
        // Request eviction of at least one entry
        Set<String> victims = lfuPolicy.selectVictims(100);
        
        // Property: At least one victim should have the minimum access count
        if (!victims.isEmpty()) {
            boolean hasMinAccessEntry = false;
            for (String victim : victims) {
                if (keysWithMinAccess.contains(victim)) {
                    hasMinAccessEntry = true;
                    break;
                }
            }
            assertTrue(hasMinAccessEntry, 
                "LFU eviction should select at least one entry with minimum access count. " +
                "Min access count: " + minAccessCount + ", Keys with min access: " + keysWithMinAccess + 
                ", Victims: " + victims);
        }
    }
    
    /**
     * Property: LFU eviction maintains frequency ordering
     * 
     * When multiple entries are evicted, they should be selected in order
     * of increasing access frequency.
     */
    @Property
    void lfuEvictionMaintainsFrequencyOrdering(
            @ForAll @IntRange(min = 3, max = 15) int numEntries) {
        
        LFUEvictionPolicy<String, String> lfuPolicy = new LFUEvictionPolicy<>();
        
        // Create entries with strictly increasing access frequencies
        Map<String, Integer> keyToFrequency = new HashMap<>();
        for (int i = 0; i < numEntries; i++) {
            String key = "key_" + i;
            int frequency = i; // 0, 1, 2, 3, ...
            
            lfuPolicy.onPut(key, "value" + i);
            
            // Access the key 'frequency' times
            for (int j = 0; j < frequency; j++) {
                lfuPolicy.onAccess(key);
            }
            
            keyToFrequency.put(key, frequency);
        }
        
        // Request eviction of multiple entries
        Set<String> victims = lfuPolicy.selectVictims(500);
        
        // Property: All victims should have frequency <= any non-victim
        if (victims.size() > 0 && victims.size() < numEntries) {
            int maxVictimFrequency = victims.stream()
                .mapToInt(keyToFrequency::get)
                .max()
                .orElse(-1);
            
            Set<String> allKeys = keyToFrequency.keySet();
            int minNonVictimFrequency = allKeys.stream()
                .filter(k -> !victims.contains(k))
                .mapToInt(keyToFrequency::get)
                .min()
                .orElse(Integer.MAX_VALUE);
            
            assertTrue(maxVictimFrequency <= minNonVictimFrequency,
                "All evicted entries should have frequency <= all non-evicted entries. " +
                "Max victim frequency: " + maxVictimFrequency + 
                ", Min non-victim frequency: " + minNonVictimFrequency);
        }
    }
    
    /**
     * Property: LFU eviction respects access count updates
     * 
     * If an entry's access count is increased, it should be less likely
     * to be evicted than entries with lower counts.
     */
    @Property
    void lfuEvictionRespectsAccessCountUpdates(
            @ForAll @IntRange(min = 1, max = 50) int additionalAccesses) {
        
        LFUEvictionPolicy<String, String> lfuPolicy = new LFUEvictionPolicy<>();
        
        String key1 = "key_1";
        String key2 = "key_2";
        
        // Add both entries with initial access count of 0
        lfuPolicy.onPut(key1, "value1");
        lfuPolicy.onPut(key2, "value2");
        
        // Access key2 multiple times
        for (int i = 0; i < additionalAccesses; i++) {
            lfuPolicy.onAccess(key2);
        }
        
        // Request eviction of one entry
        Set<String> victims = lfuPolicy.selectVictims(100);
        
        // Property: key1 (with lower access count) should be evicted before key2
        if (victims.size() == 1) {
            assertTrue(victims.contains(key1),
                "Entry with lower access count should be evicted first. " +
                "key1 accesses: 0, key2 accesses: " + additionalAccesses + 
                ", Victim: " + victims);
        }
    }
}
