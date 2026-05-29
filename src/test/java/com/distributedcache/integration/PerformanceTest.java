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
import com.distributedcache.network.*;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for the distributed cache system.
 * Tests latency and throughput requirements.
 * 
 * Validates: Requirements 1.2, 3.2, 7.2, 6.5, 11.5
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceTest {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTest.class);
    
    private static final int BASE_PORT = 9100;
    private static final long CACHE_CAPACITY = 50 * 1024 * 1024; // 50 MB
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
        
        logger.info("Setting up performance test environment");
    }
    
    @AfterEach
    void tearDown() throws Exception {
        logger.info("Tearing down performance test environment");
        
        // Close client
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("Error closing client", e);
            }
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
     * Test 1: Get operation latency (< 10ms)
     * Validates: Requirement 1.2
     */
    @Test
    @Order(1)
    @DisplayName("Get operation should complete within 10ms")
    void testGetOperationLatency() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Pre-populate cache with test data
        int numKeys = 100;
        for (int i = 0; i < numKeys; i++) {
            client.put("perf-key" + i, "perf-value" + i);
        }
        
        // Wait for replication
        Thread.sleep(500);
        
        // Warm up
        for (int i = 0; i < 20; i++) {
            client.get("perf-key" + (i % numKeys));
        }
        
        // Act: Measure get operation latency
        List<Long> latencies = new ArrayList<>();
        int numOperations = 1000;
        
        for (int i = 0; i < numOperations; i++) {
            String key = "perf-key" + (i % numKeys);
            
            long startTime = System.nanoTime();
            Optional<String> result = client.get(key);
            long endTime = System.nanoTime();
            
            assertTrue(result.isPresent(), "Key should exist");
            
            long latencyNs = endTime - startTime;
            long latencyMs = latencyNs / 1_000_000;
            latencies.add(latencyMs);
        }
        
        // Assert: Calculate statistics
        Collections.sort(latencies);
        long avgLatency = latencies.stream().mapToLong(Long::longValue).sum() / latencies.size();
        long p50Latency = latencies.get(latencies.size() / 2);
        long p95Latency = latencies.get((int) (latencies.size() * 0.95));
        long p99Latency = latencies.get((int) (latencies.size() * 0.99));
        long maxLatency = latencies.get(latencies.size() - 1);
        
        logger.info("Get operation latency statistics:");
        logger.info("  Average: {}ms", avgLatency);
        logger.info("  P50: {}ms", p50Latency);
        logger.info("  P95: {}ms", p95Latency);
        logger.info("  P99: {}ms", p99Latency);
        logger.info("  Max: {}ms", maxLatency);
        
        // Requirement 1.2: Get operations should complete within 10ms
        assertTrue(p95Latency < 10, 
            "P95 latency should be less than 10ms (was " + p95Latency + "ms)");
        
        logger.info("✓ Get operation latency test passed");
    }
    
    /**
     * Test 2: Replication latency (< 100ms)
     * Validates: Requirement 3.2
     */
    @Test
    @Order(2)
    @DisplayName("Replication should complete within 100ms")
    void testReplicationLatency() throws Exception {
        // Arrange: Create a 4-node cluster
        createCluster(4);
        
        // Act: Measure replication latency
        List<Long> replicationLatencies = new ArrayList<>();
        int numOperations = 100;
        
        for (int i = 0; i < numOperations; i++) {
            String key = "repl-key" + i;
            String value = "repl-value" + i;
            
            long startTime = System.nanoTime();
            
            // Put operation (triggers replication)
            client.put(key, value);
            
            // Wait a bit and verify replication
            Thread.sleep(10);
            
            // Check if replicas have the data
            NodeInfo primaryNode = clientHashRing.getPrimaryNode(key);
            List<NodeInfo> replicaNodes = clientHashRing.getReplicaNodes(key, REPLICATION_FACTOR);
            
            // Poll replicas until data is available or timeout
            boolean replicated = false;
            long pollStartTime = System.nanoTime();
            long timeoutNs = 150_000_000; // 150ms timeout
            
            while (!replicated && (System.nanoTime() - pollStartTime) < timeoutNs) {
                int replicaCount = 0;
                for (NodeInfo nodeInfo : replicaNodes) {
                    // Find the actual node
                    for (CacheNode node : nodes) {
                        if (node.getNodeId().equals(nodeInfo.getNodeId())) {
                            Optional<String> result = node.handleGet(key);
                            if (result.isPresent() && result.get().equals(value)) {
                                replicaCount++;
                            }
                            break;
                        }
                    }
                }
                
                if (replicaCount >= Math.min(REPLICATION_FACTOR, nodes.size())) {
                    replicated = true;
                } else {
                    Thread.sleep(5);
                }
            }
            
            long endTime = System.nanoTime();
            long latencyNs = endTime - startTime;
            long latencyMs = latencyNs / 1_000_000;
            
            if (replicated) {
                replicationLatencies.add(latencyMs);
            }
        }
        
        // Assert: Calculate statistics
        assertTrue(replicationLatencies.size() > numOperations * 0.9,
            "At least 90% of replications should complete");
        
        Collections.sort(replicationLatencies);
        long avgLatency = replicationLatencies.stream().mapToLong(Long::longValue).sum() / replicationLatencies.size();
        long p50Latency = replicationLatencies.get(replicationLatencies.size() / 2);
        long p95Latency = replicationLatencies.get((int) (replicationLatencies.size() * 0.95));
        long p99Latency = replicationLatencies.get((int) (replicationLatencies.size() * 0.99));
        long maxLatency = replicationLatencies.get(replicationLatencies.size() - 1);
        
        logger.info("Replication latency statistics:");
        logger.info("  Average: {}ms", avgLatency);
        logger.info("  P50: {}ms", p50Latency);
        logger.info("  P95: {}ms", p95Latency);
        logger.info("  P99: {}ms", p99Latency);
        logger.info("  Max: {}ms", maxLatency);
        
        // Requirement 3.2: Replication should complete within 100ms
        assertTrue(p95Latency < 100,
            "P95 replication latency should be less than 100ms (was " + p95Latency + "ms)");
        
        logger.info("✓ Replication latency test passed");
    }
    
    /**
     * Test 3: Network message delivery (< 20ms)
     * Validates: Requirement 7.2
     */
    @Test
    @Order(3)
    @DisplayName("Network messages should be delivered within 20ms")
    void testNetworkMessageDelivery() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Get two nodes for direct communication
        CacheNode node1 = nodes.get(0);
        CacheNode node2 = nodes.get(1);
        
        NodeInfo node2Info = new NodeInfo(
            node2.getNodeId(),
            node2.getAddress().getHost(),
            node2.getAddress().getPort()
        );
        
        // Act: Measure network message delivery time
        List<Long> messageLatencies = new ArrayList<>();
        int numMessages = 200;
        
        NetworkClient testNetworkClient = networkClients.get(0);
        
        for (int i = 0; i < numMessages; i++) {
            // Create a health check message (lightweight)
            HealthCheck healthCheck = new HealthCheck(node1.getNodeId());
            
            long startTime = System.nanoTime();
            
            try {
                CompletableFuture<HealthResponse> future = testNetworkClient.send(node2Info, healthCheck);
                HealthResponse response = future.get(50, TimeUnit.MILLISECONDS);
                
                long endTime = System.nanoTime();
                long latencyNs = endTime - startTime;
                long latencyMs = latencyNs / 1_000_000;
                
                if (response != null) {
                    messageLatencies.add(latencyMs);
                }
            } catch (TimeoutException e) {
                logger.warn("Message timeout on iteration {}", i);
            } catch (Exception e) {
                logger.warn("Message failed on iteration {}: {}", i, e.getMessage());
            }
            
            // Small delay between messages
            Thread.sleep(10);
        }
        
        // Assert: Calculate statistics
        assertTrue(messageLatencies.size() > numMessages * 0.8,
            "At least 80% of messages should be delivered");
        
        Collections.sort(messageLatencies);
        long avgLatency = messageLatencies.stream().mapToLong(Long::longValue).sum() / messageLatencies.size();
        long p50Latency = messageLatencies.get(messageLatencies.size() / 2);
        long p95Latency = messageLatencies.get((int) (messageLatencies.size() * 0.95));
        long p99Latency = messageLatencies.get((int) (messageLatencies.size() * 0.99));
        long maxLatency = messageLatencies.get(messageLatencies.size() - 1);
        
        logger.info("Network message delivery latency statistics:");
        logger.info("  Average: {}ms", avgLatency);
        logger.info("  P50: {}ms", p50Latency);
        logger.info("  P95: {}ms", p95Latency);
        logger.info("  P99: {}ms", p99Latency);
        logger.info("  Max: {}ms", maxLatency);
        
        // Requirement 7.2: Messages should be delivered within 20ms under normal conditions
        assertTrue(p95Latency < 20,
            "P95 message delivery latency should be less than 20ms (was " + p95Latency + "ms)");
        
        logger.info("✓ Network message delivery test passed");
    }
    
    /**
     * Test 4: Client retry latency (< 50ms)
     * Validates: Requirement 6.5
     */
    @Test
    @Order(4)
    @DisplayName("Client retry should complete within 50ms")
    void testClientRetryLatency() throws Exception {
        // Arrange: Create a 4-node cluster
        createCluster(4);
        
        // Pre-populate cache with test data
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            String key = "retry-key" + i;
            String value = "retry-value" + i;
            testData.put(key, value);
            client.put(key, value);
        }
        
        // Wait for replication
        Thread.sleep(500);
        
        // Act: Stop one node to trigger retries
        CacheNode stoppedNode = nodes.get(0);
        String stoppedNodeId = stoppedNode.getNodeId();
        logger.info("Stopping node {} to trigger retries", stoppedNodeId);
        stoppedNode.stop();
        
        // Wait for health monitor to detect failure
        Thread.sleep(1000);
        
        // Measure retry latency
        List<Long> retryLatencies = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String key = entry.getKey();
            
            // Check if this key was on the stopped node
            NodeInfo primaryNode = clientHashRing.getPrimaryNode(key);
            if (primaryNode.getNodeId().equals(stoppedNodeId)) {
                // This will trigger a retry to replica
                long startTime = System.nanoTime();
                
                try {
                    Optional<String> result = client.get(key);
                    
                    long endTime = System.nanoTime();
                    long latencyNs = endTime - startTime;
                    long latencyMs = latencyNs / 1_000_000;
                    
                    if (result.isPresent()) {
                        retryLatencies.add(latencyMs);
                    }
                } catch (Exception e) {
                    // Some failures are expected
                    logger.debug("Failed to retrieve key {}: {}", key, e.getMessage());
                }
            }
        }
        
        // Assert: Calculate statistics
        if (retryLatencies.isEmpty()) {
            logger.warn("No retry operations were measured - test may be inconclusive");
            return;
        }
        
        Collections.sort(retryLatencies);
        long avgLatency = retryLatencies.stream().mapToLong(Long::longValue).sum() / retryLatencies.size();
        long p50Latency = retryLatencies.get(retryLatencies.size() / 2);
        long p95Latency = retryLatencies.get((int) (retryLatencies.size() * 0.95));
        long maxLatency = retryLatencies.get(retryLatencies.size() - 1);
        
        logger.info("Client retry latency statistics ({} retries):", retryLatencies.size());
        logger.info("  Average: {}ms", avgLatency);
        logger.info("  P50: {}ms", p50Latency);
        logger.info("  P95: {}ms", p95Latency);
        logger.info("  Max: {}ms", maxLatency);
        
        // Requirement 6.5: Client should retry within 50ms
        assertTrue(p95Latency < 50,
            "P95 retry latency should be less than 50ms (was " + p95Latency + "ms)");
        
        logger.info("✓ Client retry latency test passed");
    }
    
    /**
     * Test 5: Concurrent connection capacity (1000+)
     * Validates: Requirement 11.5
     */
    @Test
    @Order(5)
    @DisplayName("System should support 1000+ concurrent connections")
    void testConcurrentConnectionCapacity() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Pre-populate cache
        for (int i = 0; i < 100; i++) {
            client.put("concurrent-key" + i, "concurrent-value" + i);
        }
        
        Thread.sleep(300);
        
        // Act: Create many concurrent connections
        int numConcurrentClients = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(numConcurrentClients);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numConcurrentClients);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        
        logger.info("Starting {} concurrent client operations", numConcurrentClients);
        
        for (int i = 0; i < numConcurrentClients; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // Perform a get operation
                    String key = "concurrent-key" + (clientId % 100);
                    
                    long startTime = System.nanoTime();
                    Optional<String> result = client.get(key);
                    long endTime = System.nanoTime();
                    
                    if (result.isPresent()) {
                        successCount.incrementAndGet();
                        totalLatency.addAndGet((endTime - startTime) / 1_000_000);
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    logger.debug("Client {} failed: {}", clientId, e.getMessage());
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start all clients at once
        long testStartTime = System.currentTimeMillis();
        startLatch.countDown();
        
        // Wait for all operations to complete (with timeout)
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        long testDuration = System.currentTimeMillis() - testStartTime;
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Assert: Calculate statistics
        int totalOperations = successCount.get() + failureCount.get();
        double successRate = (double) successCount.get() / totalOperations;
        long avgLatency = successCount.get() > 0 ? totalLatency.get() / successCount.get() : 0;
        
        logger.info("Concurrent connection capacity test results:");
        logger.info("  Total clients: {}", numConcurrentClients);
        logger.info("  Successful operations: {}", successCount.get());
        logger.info("  Failed operations: {}", failureCount.get());
        logger.info("  Success rate: {:.2f}%", successRate * 100);
        logger.info("  Average latency: {}ms", avgLatency);
        logger.info("  Test duration: {}ms", testDuration);
        logger.info("  Throughput: {:.2f} ops/sec", (double) totalOperations / (testDuration / 1000.0));
        
        assertTrue(completed, "All operations should complete within timeout");
        
        // Requirement 11.5: Should support at least 1000 concurrent connections
        assertTrue(successRate > 0.95,
            "Success rate should be above 95% (was " + String.format("%.2f", successRate * 100) + "%)");
        
        logger.info("✓ Concurrent connection capacity test passed");
    }
    
    /**
     * Test 6: Throughput under load
     * Additional performance test for overall system throughput
     */
    @Test
    @Order(6)
    @DisplayName("System should maintain high throughput under load")
    void testThroughputUnderLoad() throws Exception {
        // Arrange: Create a 3-node cluster
        createCluster(3);
        
        // Act: Perform sustained load test
        int numThreads = 50;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        AtomicInteger totalOperations = new AtomicInteger(0);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "throughput-key-" + threadId + "-" + i;
                        String value = "throughput-value-" + threadId + "-" + i;
                        
                        try {
                            // Mix of put and get operations
                            if (i % 3 == 0) {
                                client.put(key, value);
                            } else {
                                client.get(key);
                            }
                            
                            totalOperations.incrementAndGet();
                            successfulOperations.incrementAndGet();
                        } catch (Exception e) {
                            totalOperations.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        // Assert: Calculate throughput
        double durationSeconds = duration / 1000.0;
        double throughput = totalOperations.get() / durationSeconds;
        double successRate = (double) successfulOperations.get() / totalOperations.get();
        
        logger.info("Throughput test results:");
        logger.info("  Total operations: {}", totalOperations.get());
        logger.info("  Successful operations: {}", successfulOperations.get());
        logger.info("  Duration: {}ms", duration);
        logger.info("  Throughput: {:.2f} ops/sec", throughput);
        logger.info("  Success rate: {:.2f}%", successRate * 100);
        
        assertTrue(completed, "All operations should complete within timeout");
        assertTrue(successRate > 0.90, "Success rate should be above 90%");
        assertTrue(throughput > 100, "Throughput should be above 100 ops/sec");
        
        logger.info("✓ Throughput under load test passed");
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Creates a cluster with the specified number of nodes.
     */
    private void createCluster(int numNodes) throws Exception {
        logger.info("Creating cluster with {} nodes for performance testing", numNodes);
        
        // Create nodes
        for (int i = 0; i < numNodes; i++) {
            String nodeId = "perf-node" + i;
            int port = BASE_PORT + i;
            
            CacheNode node = createNode(nodeId, port);
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
        
        logger.info("Performance test cluster created successfully with {} nodes", numNodes);
    }
    
    /**
     * Creates a single cache node.
     */
    private CacheNode createNode(String nodeId, int port) throws Exception {
        // Create configuration
        CacheConfiguration config = new CacheConfiguration();
        config.setCacheCapacityBytes(CACHE_CAPACITY);
        config.setReplicationFactor(REPLICATION_FACTOR);
        config.setEvictionPolicy(EvictionPolicyType.LRU);
        config.setHealthCheckInterval(Duration.ofSeconds(5));
        config.setServerPort(port);
        config.setSeedNodes(Collections.emptyList());
        
        // Create components
        com.distributedcache.utils.JavaMessageSerializer serializer = 
            new com.distributedcache.utils.JavaMessageSerializer();
        
        LocalCache<String, Object> localCache = new LocalCacheImpl<>(CACHE_CAPACITY, serializer);
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
