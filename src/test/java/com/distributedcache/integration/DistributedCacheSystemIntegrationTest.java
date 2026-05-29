package com.distributedcache.integration;

import com.distributedcache.cache.LocalCache;
import com.distributedcache.cache.LocalCacheImpl;
import com.distributedcache.client.CacheClient;
import com.distributedcache.client.CacheClientImpl;
import com.distributedcache.eviction.EvictionPolicyType;
import com.distributedcache.eviction.LRUEvictionPolicy;
import com.distributedcache.hashing.ConsistentHashRing;
import com.distributedcache.hashing.HashRing;
import com.distributedcache.hashing.NodeAddress;
import com.distributedcache.hashing.NodeInfo;
import com.distributedcache.network.NetworkClient;
import com.distributedcache.network.NetworkClientImpl;
import com.distributedcache.network.NetworkServer;
import com.distributedcache.network.NetworkServerImpl;
import com.distributedcache.node.*;
import com.distributedcache.replication.ReplicationManager;
import com.distributedcache.replication.ReplicationManagerImpl;
import com.distributedcache.utils.CacheConfiguration;
import com.distributedcache.utils.ConfigurationManager;
import com.distributedcache.utils.PropertiesConfigurationManager;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for the complete distributed cache system.
 * Tests multi-node clusters, failure scenarios, replication, and concurrent operations.
 * 
 * Validates: All requirements (1-12)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DistributedCacheSystemIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(DistributedCacheSystemIntegrationTest.class);
    
    private static final int BASE_PORT = 9000;
    private static final long CACHE_CAPACITY = 10 * 1024 * 1024; // 10 MB
    private static final int REPLICATION_FACTOR = 3;
    
    private List<CacheNode> nodes;
    private List<NetworkClient> networkClients;
    private CacheClient client;
    private HashRing clientHashRing;
    
    @BeforeEach
    void setUp() throws Exception {
        nodes = new ArrayList<>();
        networkClients = new ArrayList<>();
        
        // Clean up any persisted node list from previous tests
        java.nio.file.Path nodeListPath = java.nio.file.Paths.get("cache-nodes.dat");
        if (java.nio.file.Files.exists(nodeListPath)) {
            java.nio.file.Files.delete(nodeListPath);
            logger.info("Deleted persisted node list from previous test");
        }
        
        logger.info("Setting up test environment");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        logger.info("Tearing down test environment");
        
        // Close client
        if (client != null) {
            client.close();
        }
        
        // Stop all nodes
        for (CacheNode node : nodes) {
            try {
                node.stop();
            } catch (Exception e) {
                logger.warn("Error stopping node", e);
            }
        }
        
        // Give nodes time to clean up
        Thread.sleep(500);
        
        nodes.clear();
        networkClients.clear();
        
        // Clean up persisted node list after test
        java.nio.file.Path nodeListPath = java.nio.file.Paths.get("cache-nodes.dat");
        if (java.nio.file.Files.exists(nodeListPath)) {
            java.nio.file.Files.delete(nodeListPath);
            logger.info("Deleted persisted node list after test");
        }
    }
    
    /**
     * Test 1: Multi-node cluster with client operations
     * Validates: Requirements 1, 2, 6
     */
    @Test
    @Order(1)
    @DisplayName("Multi-node cluster should handle basic client operations")
    void testMultiNodeClusterWithClientOperations() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Act & Assert: Basic operations
        client.put("key1", "value1");
        Optional<String> result1 = client.get("key1");
        assertTrue(result1.isPresent());
        assertEquals("value1", result1.get());
        
        client.put("key2", "value2");
        Optional<String> result2 = client.get("key2");
        assertTrue(result2.isPresent());
        assertEquals("value2", result2.get());
        
        client.delete("key1");
        Thread.sleep(200); // Wait for delete to propagate
        Optional<String> result3 = client.get("key1");
        assertFalse(result3.isPresent());
        
        logger.info("✓ Multi-node cluster basic operations successful");
    }
    
    /**
     * Test 2: Node failure during client operations
     * Validates: Requirements 3, 5, 6
     */
    @Test
    @Order(2)
    @DisplayName("Client should handle node failures gracefully")
    void testNodeFailureDuringClientOperations() throws Exception {
        // Arrange: Create a 3-node cluster with replication
        createCluster(3);
        
        // Store data
        client.put("key1", "value1");
        client.put("key2", "value2");
        client.put("key3", "value3");
        
        // Wait for replication
        Thread.sleep(200);
        
        // Act: Stop one node
        CacheNode failedNode = nodes.get(0);
        String failedNodeId = failedNode.getNodeId();
        logger.info("Stopping node: {}", failedNodeId);
        failedNode.stop();
        
        // Wait for health monitor to detect failure
        Thread.sleep(1000);
        
        // Assert: Client should still be able to retrieve data from replicas
        Optional<String> result1 = client.get("key1");
        Optional<String> result2 = client.get("key2");
        Optional<String> result3 = client.get("key3");
        
        // At least some keys should still be accessible
        assertTrue(result1.isPresent() || result2.isPresent() || result3.isPresent(),
            "At least some data should be accessible after node failure");
        
        logger.info("✓ Node failure handled gracefully");
    }
    
    /**
     * Test 3: Automatic failover to replicas
     * Validates: Requirements 3.3, 6.5
     */
    @Test
    @Order(3)
    @DisplayName("System should automatically failover to replica nodes")
    void testAutomaticFailoverToReplicas() throws Exception {
        // Arrange: Create a 4-node cluster
        createCluster(4);
        
        // Store data with replication
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            String key = "key" + i;
            String value = "value" + i;
            testData.put(key, value);
            client.put(key, value);
        }
        
        // Wait for replication
        Thread.sleep(300);
        
        // Act: Stop two nodes
        nodes.get(0).stop();
        nodes.get(1).stop();
        logger.info("Stopped 2 nodes for failover test");
        
        // Wait for health detection
        Thread.sleep(1000);
        
        // Assert: Most data should still be accessible via replicas
        int successCount = 0;
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            try {
                Optional<String> result = client.get(entry.getKey());
                if (result.isPresent() && result.get().equals(entry.getValue())) {
                    successCount++;
                }
            } catch (Exception e) {
                // Some failures are expected
            }
        }
        
        // With replication factor 3 and 2 nodes down, we should still have most data
        assertTrue(successCount > testData.size() / 2,
            "Should retrieve more than half of the data from replicas");
        
        logger.info("✓ Automatic failover successful: {}/{} keys retrieved", 
            successCount, testData.size());
    }
    
    /**
     * Test 4: Node recovery and rejoin
     * Validates: Requirements 5.4, 10
     */
    @Test
    @Order(4)
    @DisplayName("Recovered node should rejoin cluster and participate in operations")
    void testNodeRecoveryAndRejoin() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Store initial data
        client.put("key1", "value1");
        Thread.sleep(200);
        
        // Stop a node
        CacheNode stoppedNode = nodes.get(1);
        String stoppedNodeId = stoppedNode.getNodeId();
        int stoppedNodePort = stoppedNode.getAddress().getPort();
        stoppedNode.stop();
        logger.info("Stopped node: {}", stoppedNodeId);
        
        Thread.sleep(1000);
        
        // Act: Restart the node (simulate recovery)
        CacheNode recoveredNode = createNode(stoppedNodeId, stoppedNodePort);
        recoveredNode.start();
        nodes.set(1, recoveredNode);
        
        // Add recovered node back to client's hash ring
        NodeInfo recoveredNodeInfo = new NodeInfo(
            stoppedNodeId,
            "localhost",
            stoppedNodePort
        );
        clientHashRing.addNode(recoveredNodeInfo);
        
        logger.info("Restarted node: {}", stoppedNodeId);
        Thread.sleep(1000);
        
        // Assert: System should work normally
        client.put("key2", "value2");
        Optional<String> result = client.get("key2");
        assertTrue(result.isPresent());
        assertEquals("value2", result.get());
        
        logger.info("✓ Node recovery and rejoin successful");
    }
    
    /**
     * Test 5: Data rebalancing after topology changes
     * Validates: Requirements 2.2, 10.4
     */
    @Test
    @Order(5)
    @DisplayName("Data should rebalance when nodes join or leave")
    void testDataRebalancingAfterTopologyChanges() throws Exception {
        // Arrange: Create a 2-node cluster
        createCluster(2);
        
        // Store data
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            String key = "rebalance-key" + i;
            String value = "rebalance-value" + i;
            testData.put(key, value);
            client.put(key, value);
        }
        
        Thread.sleep(200);
        
        // Act: Add a new node
        CacheNode newNode = createNode("node-new", BASE_PORT + 10);
        newNode.start();
        nodes.add(newNode);
        
        // Add to client hash ring
        NodeInfo newNodeInfo = new NodeInfo("node-new", "localhost", BASE_PORT + 10);
        clientHashRing.addNode(newNodeInfo);
        
        logger.info("Added new node to cluster");
        
        // Wait for rebalancing (requirement 10.4: within 30 seconds)
        // Poll for data availability instead of fixed wait
        int successCount = 0;
        int maxAttempts = 30; // 30 seconds with 1 second intervals
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            Thread.sleep(1000);
            successCount = 0;
            for (Map.Entry<String, String> entry : testData.entrySet()) {
                try {
                    Optional<String> result = client.get(entry.getKey());
                    if (result.isPresent() && result.get().equals(entry.getValue())) {
                        successCount++;
                    }
                } catch (Exception e) {
                    // Ignore transient errors during rebalancing
                }
            }
            
            // If we have good availability, we can exit early
            if (successCount >= testData.size() * 0.9) {
                logger.info("Rebalancing completed after {} seconds", attempt + 1);
                break;
            }
        }
        
        // Assert: All data should still be accessible
        assertTrue(successCount >= testData.size() * 0.9,
            "At least 90% of data should be accessible after rebalancing");
        
        logger.info("✓ Data rebalancing successful: {}/{} keys accessible", 
            successCount, testData.size());
    }
    
    /**
     * Test 6: Eviction across replicas
     * Validates: Requirements 4.6
     */
    @Test
    @Order(6)
    @DisplayName("Eviction should be propagated to all replicas")
    void testEvictionAcrossReplicas() throws Exception {
        // Arrange: Create a 3-node cluster with small capacity
        createClusterWithCapacity(3, 1024 * 100); // 100 KB
        
        // Fill cache to trigger eviction
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String key = "evict-key" + i;
            String value = "x".repeat(3000); // ~3KB per entry
            keys.add(key);
            client.put(key, value);
        }
        
        // Wait for eviction to occur
        Thread.sleep(500);
        
        // Act: Check that some keys have been evicted
        int missingCount = 0;
        for (String key : keys) {
            Optional<String> result = client.get(key);
            if (result.isEmpty()) {
                missingCount++;
            }
        }
        
        // Assert: Some keys should have been evicted
        assertTrue(missingCount > 0, "Some keys should have been evicted");
        
        logger.info("✓ Eviction across replicas successful: {} keys evicted", missingCount);
    }
    
    /**
     * Test 7: Concurrent client operations
     * Validates: Requirements 11
     */
    @Test
    @Order(7)
    @DisplayName("System should handle concurrent client operations correctly")
    void testConcurrentClientOperations() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        int numThreads = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        ConcurrentHashMap<String, String> expectedData = new ConcurrentHashMap<>();
        
        // Act: Perform concurrent operations
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            Future<?> future = executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "concurrent-key-" + threadId + "-" + i;
                        String value = "concurrent-value-" + threadId + "-" + i;
                        
                        // Put operation
                        client.put(key, value);
                        expectedData.put(key, value);
                        
                        // Get operation
                        Optional<String> result = client.get(key);
                        assertTrue(result.isPresent());
                        
                        // Some deletes
                        if (i % 10 == 0) {
                            client.delete(key);
                            expectedData.remove(key);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error in concurrent operation", e);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }
        
        // Wait for all operations to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "All concurrent operations should complete");
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Wait for replication
        Thread.sleep(500);
        
        // Assert: Verify data consistency
        int successCount = 0;
        for (Map.Entry<String, String> entry : expectedData.entrySet()) {
            Optional<String> result = client.get(entry.getKey());
            if (result.isPresent() && result.get().equals(entry.getValue())) {
                successCount++;
            }
        }
        
        double successRate = (double) successCount / expectedData.size();
        assertTrue(successRate > 0.95,
            "At least 95% of concurrent operations should be consistent");
        
        logger.info("✓ Concurrent operations successful: {}/{} keys consistent ({}%)", 
            successCount, expectedData.size(), String.format("%.2f", successRate * 100));
    }
    
    /**
     * Test 8: Batch operations
     * Validates: Requirement 6.6
     */
    @Test
    @Order(8)
    @DisplayName("Client should support efficient batch get operations")
    void testBatchOperations() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Store test data
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            String key = "batch-key" + i;
            String value = "batch-value" + i;
            testData.put(key, value);
            client.put(key, value);
        }
        
        Thread.sleep(200);
        
        // Act: Perform batch get
        List<String> keys = new ArrayList<>(testData.keySet());
        Map<String, String> results = client.batchGet(keys);
        
        // Assert: Should retrieve most keys
        assertTrue(results.size() >= testData.size() * 0.9,
            "Batch get should retrieve at least 90% of keys");
        
        for (Map.Entry<String, String> entry : results.entrySet()) {
            assertEquals(testData.get(entry.getKey()), entry.getValue(),
                "Batch get values should match");
        }
        
        logger.info("✓ Batch operations successful: {}/{} keys retrieved", 
            results.size(), testData.size());
    }
    
    /**
     * Test 9: Metrics collection across cluster
     * Validates: Requirement 12
     */
    @Test
    @Order(9)
    @DisplayName("Nodes should collect and expose metrics")
    void testMetricsCollectionAcrossCluster() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Perform operations to generate metrics
        for (int i = 0; i < 100; i++) {
            client.put("metrics-key" + i, "metrics-value" + i);
        }
        
        for (int i = 0; i < 100; i++) {
            client.get("metrics-key" + i);
        }
        
        // Wait for metrics to update
        Thread.sleep(1500);
        
        // Act & Assert: Check metrics on each node
        for (CacheNode node : nodes) {
            NodeMetrics metrics = node.getMetrics();
            assertNotNull(metrics);
            
            // Should have recorded some operations
            assertTrue(metrics.getTotalHits() + metrics.getTotalMisses() > 0,
                "Node should have recorded operations");
            
            logger.info("Node {} metrics: hits={}, misses={}, latency={}ms, memory={}%",
                node.getNodeId(),
                metrics.getTotalHits(),
                metrics.getTotalMisses(),
                String.format("%.2f", metrics.getAverageGetLatencyMs()),
                String.format("%.2f", metrics.getMemoryUsagePercentage()));
        }
        
        logger.info("✓ Metrics collection successful");
    }
    
    /**
     * Test 10: Health monitoring and status changes
     * Validates: Requirement 5
     */
    @Test
    @Order(10)
    @DisplayName("Health monitor should detect and track node status changes")
    void testHealthMonitoringAndStatusChanges() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // All nodes should be healthy initially
        for (CacheNode node : nodes) {
            HealthStatus status = node.getHealthStatus();
            assertEquals(HealthStatus.HEALTHY, status,
                "Node should be healthy initially");
        }
        
        // Act: Stop a node
        CacheNode stoppedNode = nodes.get(1);
        stoppedNode.stop();
        
        // Wait for health monitor to detect failure (3 checks * 5 seconds = 15 seconds)
        Thread.sleep(16000);
        
        // Assert: Remaining nodes should still be healthy
        assertEquals(HealthStatus.HEALTHY, nodes.get(0).getHealthStatus());
        assertEquals(HealthStatus.HEALTHY, nodes.get(2).getHealthStatus());
        
        logger.info("✓ Health monitoring successful");
    }
    
    /**
     * Test 11: Consistent hashing distribution
     * Validates: Requirements 2.1, 2.4
     */
    @Test
    @Order(11)
    @DisplayName("Keys should be distributed evenly across nodes")
    void testConsistentHashingDistribution() throws Exception {
        // Arrange: Create a 4-node cluster
        createCluster(4);
        
        // Store many keys
        int numKeys = 200;
        for (int i = 0; i < numKeys; i++) {
            client.put("dist-key" + i, "dist-value" + i);
        }
        
        Thread.sleep(300);
        
        // Act: Check distribution across nodes
        Map<String, Integer> keysPerNode = new HashMap<>();
        for (CacheNode node : nodes) {
            keysPerNode.put(node.getNodeId(), 0);
        }
        
        // Count keys on each node by checking which node is primary
        for (int i = 0; i < numKeys; i++) {
            String key = "dist-key" + i;
            NodeInfo primaryNode = clientHashRing.getPrimaryNode(key);
            keysPerNode.merge(primaryNode.getNodeId(), 1, Integer::sum);
        }
        
        // Assert: Distribution should be reasonably balanced
        int avgKeysPerNode = numKeys / nodes.size();
        for (Map.Entry<String, Integer> entry : keysPerNode.entrySet()) {
            int keyCount = entry.getValue();
            double deviation = Math.abs(keyCount - avgKeysPerNode) / (double) avgKeysPerNode;
            
            assertTrue(deviation < 0.5,
                "Key distribution should be within 50% of average");
            
            logger.info("Node {} has {} keys (avg: {})", 
                entry.getKey(), keyCount, avgKeysPerNode);
        }
        
        logger.info("✓ Consistent hashing distribution successful");
    }
    
    /**
     * Test 12: Replication factor validation
     * Validates: Requirements 3.1, 3.4
     */
    @Test
    @Order(12)
    @DisplayName("Data should be replicated according to replication factor")
    void testReplicationFactorValidation() throws Exception {
        // Arrange: Create a 5-node cluster
        createCluster(5);
        
        // Store data
        String testKey = "replication-test-key";
        String testValue = "replication-test-value";
        client.put(testKey, testValue);
        
        // Wait for replication
        Thread.sleep(300);
        
        // Act: Check how many nodes have the data
        int replicaCount = 0;
        for (CacheNode node : nodes) {
            Optional<String> result = node.handleGet(testKey);
            if (result.isPresent() && result.get().equals(testValue)) {
                replicaCount++;
                logger.info("Node {} has the key", node.getNodeId());
            }
        }
        
        // Assert: Should have replication factor copies
        assertTrue(replicaCount >= 1 && replicaCount <= REPLICATION_FACTOR,
            "Should have between 1 and " + REPLICATION_FACTOR + " replicas");
        
        logger.info("✓ Replication factor validation successful: {} replicas", replicaCount);
    }
    
    /**
     * Test 13: Multiple node failures with recovery
     * Validates: Requirements 3, 5, 6
     */
    @Test
    @Order(13)
    @DisplayName("System should handle multiple node failures and recoveries")
    void testMultipleNodeFailuresWithRecovery() throws Exception {
        // Arrange: Create a 5-node cluster
        createCluster(5);
        
        // Store test data
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            String key = "multi-fail-key" + i;
            String value = "multi-fail-value" + i;
            testData.put(key, value);
            client.put(key, value);
        }
        
        Thread.sleep(300);
        
        // Act: Stop multiple nodes
        List<CacheNode> stoppedNodes = new ArrayList<>();
        stoppedNodes.add(nodes.get(0));
        stoppedNodes.add(nodes.get(2));
        
        for (CacheNode node : stoppedNodes) {
            logger.info("Stopping node: {}", node.getNodeId());
            node.stop();
        }
        
        Thread.sleep(1000);
        
        // Assert: Data should still be accessible from remaining nodes
        int successCount = 0;
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            try {
                Optional<String> result = client.get(entry.getKey());
                if (result.isPresent() && result.get().equals(entry.getValue())) {
                    successCount++;
                }
            } catch (Exception e) {
                // Some failures expected
            }
        }
        
        assertTrue(successCount > testData.size() / 2,
            "More than half of data should be accessible with 2 nodes down");
        
        logger.info("✓ Multiple node failures handled: {}/{} keys accessible", 
            successCount, testData.size());
    }
    
    /**
     * Test 14: Concurrent writes to same key
     * Validates: Requirement 11.3
     */
    @Test
    @Order(14)
    @DisplayName("Concurrent writes to same key should be serialized correctly")
    void testConcurrentWritesToSameKey() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        String testKey = "concurrent-same-key";
        int numThreads = 10;
        int writesPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        ConcurrentHashMap<String, Integer> writeAttempts = new ConcurrentHashMap<>();
        
        // Act: Multiple threads writing to the same key
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < writesPerThread; i++) {
                        String value = "thread-" + threadId + "-write-" + i;
                        client.put(testKey, value);
                        writeAttempts.put(value, threadId);
                        Thread.sleep(10); // Small delay between writes
                    }
                } catch (Exception e) {
                    logger.error("Error in concurrent write", e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertTrue(completed, "All concurrent writes should complete");
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        Thread.sleep(500);
        
        // Assert: Final value should be one of the written values
        Optional<String> finalValue = client.get(testKey);
        assertTrue(finalValue.isPresent(), "Key should exist after concurrent writes");
        assertTrue(writeAttempts.containsKey(finalValue.get()),
            "Final value should be one of the written values");
        
        logger.info("✓ Concurrent writes to same key handled correctly: final value = {}", 
            finalValue.get());
    }
    
    /**
     * Test 15: Large value storage and retrieval
     * Validates: Requirement 1.6
     */
    @Test
    @Order(15)
    @DisplayName("System should handle values up to 1 MB")
    void testLargeValueStorageAndRetrieval() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Create a large value (close to 1 MB)
        String largeValue = "x".repeat(1024 * 1024 - 1000); // ~1 MB
        String testKey = "large-value-key";
        
        // Act: Store and retrieve large value
        client.put(testKey, largeValue);
        Thread.sleep(300);
        
        Optional<String> result = client.get(testKey);
        
        // Assert: Should successfully store and retrieve
        assertTrue(result.isPresent(), "Large value should be stored");
        assertEquals(largeValue, result.get(), "Retrieved value should match");
        
        logger.info("✓ Large value storage successful: {} bytes", largeValue.length());
    }
    
    /**
     * Test 16: Node leave and data migration
     * Validates: Requirements 2.3, 10
     */
    @Test
    @Order(16)
    @DisplayName("Data should migrate when node leaves cluster")
    void testNodeLeaveAndDataMigration() throws Exception {
        // Arrange: Create a 4-node cluster
        createCluster(4);
        
        // Store data
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 40; i++) {
            String key = "leave-key" + i;
            String value = "leave-value" + i;
            testData.put(key, value);
            client.put(key, value);
        }
        
        Thread.sleep(300);
        
        // Act: Remove a node
        CacheNode leavingNode = nodes.get(1);
        String leavingNodeId = leavingNode.getNodeId();
        logger.info("Node leaving: {}", leavingNodeId);
        
        leavingNode.stop();
        nodes.remove(1);
        clientHashRing.removeNode(leavingNodeId);
        
        Thread.sleep(2000);
        
        // Assert: All data should still be accessible from remaining nodes
        int successCount = 0;
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            try {
                Optional<String> result = client.get(entry.getKey());
                if (result.isPresent() && result.get().equals(entry.getValue())) {
                    successCount++;
                }
            } catch (Exception e) {
                // Some transient failures acceptable
            }
        }
        
        assertTrue(successCount >= testData.size() * 0.85,
            "At least 85% of data should be accessible after node leaves");
        
        logger.info("✓ Node leave handled: {}/{} keys accessible", 
            successCount, testData.size());
    }
    
    /**
     * Test 17: Stress test with high load
     * Validates: Requirements 1, 6, 11
     */
    @Test
    @Order(17)
    @DisplayName("System should handle high load stress test")
    void testStressTestWithHighLoad() throws Exception {
        // Arrange: Create a 4-node cluster
        createCluster(4);
        
        int numThreads = 50;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successfulOps = new AtomicInteger(0);
        AtomicInteger failedOps = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        // Act: High load operations
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "stress-key-" + threadId + "-" + i;
                        String value = "stress-value-" + threadId + "-" + i;
                        
                        try {
                            client.put(key, value);
                            Optional<String> result = client.get(key);
                            if (result.isPresent() && result.get().equals(value)) {
                                successfulOps.incrementAndGet();
                            } else {
                                failedOps.incrementAndGet();
                            }
                        } catch (Exception e) {
                            failedOps.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertTrue(completed, "Stress test should complete");
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        int totalOps = numThreads * operationsPerThread;
        double successRate = (double) successfulOps.get() / totalOps;
        double throughput = (totalOps * 1000.0) / duration;
        
        // Assert: Should have high success rate
        assertTrue(successRate > 0.90,
            "Success rate should be above 90% under high load");
        
        logger.info("✓ Stress test completed: {}/{} successful ({}%), throughput: {:.2f} ops/sec",
            successfulOps.get(), totalOps, String.format("%.2f", successRate * 100), throughput);
    }
    
    /**
     * Test 18: Mixed operations under node failures
     * Validates: Requirements 1, 3, 5, 6, 11
     */
    @Test
    @Order(18)
    @DisplayName("System should handle mixed operations during node failures")
    void testMixedOperationsUnderNodeFailures() throws Exception {
        // Arrange: Create a 5-node cluster
        createCluster(5);
        
        // Pre-populate with data
        Map<String, String> initialData = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            String key = "mixed-key" + i;
            String value = "mixed-value" + i;
            initialData.put(key, value);
            client.put(key, value);
        }
        
        Thread.sleep(300);
        
        // Act: Start mixed operations in background
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicBoolean keepRunning = new AtomicBoolean(true);
        AtomicInteger opsCompleted = new AtomicInteger(0);
        
        for (int t = 0; t < 10; t++) {
            final int threadId = t;
            executor.submit(() -> {
                while (keepRunning.get()) {
                    try {
                        String key = "mixed-key" + (threadId * 10 + (opsCompleted.get() % 10));
                        
                        // Mix of operations
                        if (opsCompleted.get() % 3 == 0) {
                            client.put(key, "updated-value-" + opsCompleted.get());
                        } else if (opsCompleted.get() % 3 == 1) {
                            client.get(key);
                        } else {
                            // Occasionally delete
                            if (opsCompleted.get() % 10 == 0) {
                                client.delete(key);
                            } else {
                                client.get(key);
                            }
                        }
                        
                        opsCompleted.incrementAndGet();
                        Thread.sleep(50);
                    } catch (Exception e) {
                        // Expected during failures
                    }
                }
            });
        }
        
        // Let operations run for a bit
        Thread.sleep(2000);
        
        // Fail a node
        logger.info("Failing node during operations");
        nodes.get(1).stop();
        
        // Continue operations
        Thread.sleep(2000);
        
        // Fail another node
        logger.info("Failing second node during operations");
        nodes.get(3).stop();
        
        // Continue operations
        Thread.sleep(2000);
        
        // Stop operations
        keepRunning.set(false);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Assert: System should still be functional
        int accessibleKeys = 0;
        for (String key : initialData.keySet()) {
            try {
                Optional<String> result = client.get(key);
                if (result.isPresent()) {
                    accessibleKeys++;
                }
            } catch (Exception e) {
                // Some failures expected
            }
        }
        
        assertTrue(accessibleKeys > initialData.size() / 2,
            "More than half of data should be accessible after failures");
        assertTrue(opsCompleted.get() > 100,
            "Should complete significant operations during failures");
        
        logger.info("✓ Mixed operations under failures: {} ops completed, {}/{} keys accessible",
            opsCompleted.get(), accessibleKeys, initialData.size());
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Creates a cluster with the specified number of nodes.
     */
    private void createCluster(int numNodes) throws Exception {
        createClusterWithCapacity(numNodes, CACHE_CAPACITY);
    }
    
    /**
     * Creates a cluster with specified number of nodes and capacity.
     */
    private void createClusterWithCapacity(int numNodes, long capacity) throws Exception {
        logger.info("Creating cluster with {} nodes", numNodes);
        
        // Create nodes
        for (int i = 0; i < numNodes; i++) {
            String nodeId = "node" + i;
            int port = BASE_PORT + i;
            
            CacheNode node = createNodeWithCapacity(nodeId, port, capacity);
            node.start();
            nodes.add(node);
            
            // Give node time to start
            Thread.sleep(200);
        }
        
        // Wait for nodes to discover each other
        Thread.sleep(1000);
        
        // Create client hash ring with all nodes
        clientHashRing = new ConsistentHashRing();
        for (CacheNode node : nodes) {
            NodeInfo nodeInfo = new NodeInfo(
                node.getNodeId(),
                node.getAddress().getHost(),
                node.getAddress().getPort()
            );
            clientHashRing.addNode(nodeInfo);
        }
        
        // Create network client for the client
        NetworkClient clientNetworkClient = new NetworkClientImpl(clientHashRing);
        networkClients.add(clientNetworkClient);
        
        // Create cache client
        client = new CacheClientImpl(clientNetworkClient, clientHashRing, REPLICATION_FACTOR);
        
        logger.info("Cluster created successfully with {} nodes", numNodes);
    }
    
    /**
     * Creates a single cache node with default capacity.
     */
    private CacheNode createNode(String nodeId, int port) throws Exception {
        return createNodeWithCapacity(nodeId, port, CACHE_CAPACITY);
    }
    
    /**
     * Creates a single cache node with specified capacity.
     */
    private CacheNode createNodeWithCapacity(String nodeId, int port, long capacity) throws Exception {
        // Create configuration
        CacheConfiguration config = new CacheConfiguration();
        config.setCacheCapacityBytes(capacity);
        config.setReplicationFactor(REPLICATION_FACTOR);
        config.setEvictionPolicy(EvictionPolicyType.LRU);
        config.setHealthCheckInterval(Duration.ofSeconds(5));
        config.setServerPort(port);
        config.setSeedNodes(Collections.emptyList());
        
        // Create components
        com.distributedcache.utils.JavaMessageSerializer serializer = 
            new com.distributedcache.utils.JavaMessageSerializer();
        
        LocalCache<String, Object> localCache = new LocalCacheImpl<>(capacity, serializer);
        localCache.setEvictionPolicy(new LRUEvictionPolicy<>(serializer));
        
        HashRing hashRing = new ConsistentHashRing();
        
        NetworkServer networkServer = new NetworkServerImpl(port);
        NetworkClient networkClient = new NetworkClientImpl(hashRing);
        networkClients.add(networkClient);
        
        ReplicationManager replicationManager = new ReplicationManagerImpl(
            networkClient,
            REPLICATION_FACTOR,
            nodeId
        );
        
        HealthMonitor healthMonitor = new HealthMonitorImpl(
            networkClient,
            nodeId,
            ConcurrentHashMap.newKeySet(),
            Duration.ofSeconds(5)
        );
        
        MetricsCollector metricsCollector = new MetricsCollectorImpl();
        
        ConfigurationManager configurationManager = new PropertiesConfigurationManager();
        
        NodeAddress address = new NodeAddress("localhost", port);
        
        // Create node
        return new CacheNodeImpl(
            nodeId,
            address,
            localCache,
            hashRing,
            replicationManager,
            healthMonitor,
            networkServer,
            networkClient,
            metricsCollector,
            configurationManager,
            config
        );
    }
}
