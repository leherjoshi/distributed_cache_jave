package com.distributedcache.demo;

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
import com.distributedcache.monitoring.MetricsServer;
import com.distributedcache.network.NetworkClient;
import com.distributedcache.network.NetworkClientImpl;
import com.distributedcache.network.NetworkServer;
import com.distributedcache.network.NetworkServerImpl;
import com.distributedcache.node.*;
import com.distributedcache.replication.ReplicationManager;
import com.distributedcache.replication.ReplicationManagerImpl;
import com.distributedcache.utils.CacheConfiguration;
import com.distributedcache.utils.ConfigurationManager;
import com.distributedcache.utils.JavaMessageSerializer;
import com.distributedcache.utils.PropertiesConfigurationManager;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quick Start Demo - Run a 3-node distributed cache cluster locally
 * 
 * This demo:
 * 1. Starts 3 cache nodes on ports 8001, 8002, 8003
 * 2. Creates a client to interact with the cluster
 * 3. Performs basic operations (put, get, delete)
 * 4. Starts HTTP metrics server on port 9001
 * 5. Shows cluster metrics
 * 6. Demonstrates node failure and recovery
 */
public class QuickStartDemo {
    
    private static final int BASE_PORT = 8001;
    private static final int METRICS_PORT = 9001;
    private static final long CACHE_CAPACITY = 10 * 1024 * 1024; // 10 MB
    private static final int REPLICATION_FACTOR = 2;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("DISTRIBUTED CACHE SYSTEM - QUICK START DEMO");
        System.out.println("=".repeat(80));
        System.out.println();
        
        // Step 1: Create and start 3 cache nodes
        System.out.println("📦 Step 1: Starting 3-node cache cluster...");
        List<CacheNode> nodes = new ArrayList<>();
        
        for (int i = 0; i < 3; i++) {
            String nodeId = "node-" + (i + 1);
            int port = BASE_PORT + i;
            
            CacheNode node = createNode(nodeId, port);
            node.start();
            nodes.add(node);
            
            System.out.println("   ✓ Started " + nodeId + " on port " + port);
            Thread.sleep(500); // Give nodes time to start
        }
        
        System.out.println("   ✓ Cluster is ready!");
        System.out.println();
        
        // Wait for nodes to discover each other
        Thread.sleep(2000);
        
        // Step 2: Start HTTP metrics server
        System.out.println("📊 Step 2: Starting HTTP metrics server...");
        MetricsServer metricsServer = new MetricsServer(nodes.get(0).getMetricsCollector(), METRICS_PORT);
        metricsServer.start();
        System.out.println("   ✓ Metrics server started on port " + METRICS_PORT);
        System.out.println("   ✓ View metrics at: http://localhost:" + METRICS_PORT + "/metrics");
        System.out.println();
        
        // Step 3: Create client
        System.out.println("🔌 Step 3: Creating cache client...");
        HashRing clientHashRing = new ConsistentHashRing();
        for (CacheNode node : nodes) {
            NodeInfo nodeInfo = new NodeInfo(
                node.getNodeId(),
                node.getAddress().getHost(),
                node.getAddress().getPort()
            );
            clientHashRing.addNode(nodeInfo);
        }
        
        NetworkClient networkClient = new NetworkClientImpl(clientHashRing);
        CacheClient client = new CacheClientImpl(networkClient, clientHashRing, REPLICATION_FACTOR);
        System.out.println("   ✓ Client connected to cluster");
        System.out.println();
        
        // Step 4: Perform basic operations
        System.out.println("🚀 Step 4: Performing cache operations...");
        System.out.println();
        
        // PUT operations
        System.out.println("   📝 Storing data:");
        String[] keys = {"user:1001", "user:1002", "user:1003", "product:5001", "product:5002"};
        String[] values = {
            "Alice Johnson",
            "Bob Smith", 
            "Charlie Brown",
            "Laptop Pro 15",
            "Wireless Mouse"
        };
        
        for (int i = 0; i < keys.length; i++) {
            client.put(keys[i], values[i]);
            System.out.println("      ✓ PUT " + keys[i] + " = " + values[i]);
        }
        System.out.println();
        
        // Wait for replication
        Thread.sleep(500);
        
        // GET operations
        System.out.println("   📖 Retrieving data:");
        for (String key : keys) {
            Optional<String> value = client.get(key);
            if (value.isPresent()) {
                System.out.println("      ✓ GET " + key + " = " + value.get());
            } else {
                System.out.println("      ✗ GET " + key + " = NOT FOUND");
            }
        }
        System.out.println();
        
        // DELETE operation
        System.out.println("   🗑️  Deleting data:");
        client.delete("user:1002");
        System.out.println("      ✓ DELETE user:1002");
        
        Optional<String> deletedValue = client.get("user:1002");
        System.out.println("      ✓ Verify: user:1002 = " + (deletedValue.isEmpty() ? "NOT FOUND (correct)" : "STILL EXISTS (error)"));
        System.out.println();
        
        // Step 5: Show cluster metrics
        System.out.println("📈 Step 5: Cluster Metrics:");
        System.out.println();
        for (CacheNode node : nodes) {
            NodeMetrics metrics = node.getMetrics();
            System.out.println("   " + node.getNodeId() + ":");
            System.out.println("      - Total Hits: " + metrics.getTotalHits());
            System.out.println("      - Total Misses: " + metrics.getTotalMisses());
            System.out.println("      - Avg Latency: " + String.format("%.2f", metrics.getAverageGetLatencyMs()) + " ms");
            System.out.println("      - Memory Usage: " + String.format("%.2f", metrics.getMemoryUsagePercentage()) + "%");
            System.out.println("      - Health: " + node.getHealthStatus());
        }
        System.out.println();
        
        // Step 6: Demonstrate node failure
        System.out.println("⚠️  Step 6: Simulating node failure...");
        CacheNode failedNode = nodes.get(1);
        System.out.println("   ⏸️  Stopping " + failedNode.getNodeId() + "...");
        failedNode.stop();
        Thread.sleep(1000);
        
        System.out.println("   🔄 Attempting to retrieve data after node failure:");
        for (String key : keys) {
            try {
                Optional<String> value = client.get(key);
                if (value.isPresent()) {
                    System.out.println("      ✓ GET " + key + " = " + value.get() + " (from replica)");
                }
            } catch (Exception e) {
                System.out.println("      ✗ GET " + key + " = FAILED");
            }
        }
        System.out.println();
        
        // Step 7: Interactive mode
        System.out.println("=".repeat(80));
        System.out.println("✨ DEMO COMPLETE!");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("Your distributed cache cluster is running!");
        System.out.println();
        System.out.println("📊 View live metrics: http://localhost:" + METRICS_PORT + "/metrics");
        System.out.println("🔧 Active nodes: " + (nodes.size() - 1) + " (node-2 is stopped)");
        System.out.println();
        System.out.println("Press Ctrl+C to stop the cluster...");
        System.out.println();
        
        // Keep running
        Thread.currentThread().join();
    }
    
    private static CacheNode createNode(String nodeId, int port) throws Exception {
        // Create configuration
        CacheConfiguration config = new CacheConfiguration();
        config.setCacheCapacityBytes(CACHE_CAPACITY);
        config.setReplicationFactor(REPLICATION_FACTOR);
        config.setEvictionPolicy(EvictionPolicyType.LRU);
        config.setHealthCheckInterval(Duration.ofSeconds(5));
        config.setServerPort(port);
        config.setSeedNodes(Collections.emptyList());
        
        // Create components
        JavaMessageSerializer serializer = new JavaMessageSerializer();
        
        LocalCache<String, Object> localCache = new LocalCacheImpl<>(CACHE_CAPACITY, serializer);
        localCache.setEvictionPolicy(new LRUEvictionPolicy<>(serializer));
        
        HashRing hashRing = new ConsistentHashRing();
        
        NetworkServer networkServer = new NetworkServerImpl(port);
        NetworkClient networkClient = new NetworkClientImpl(hashRing);
        
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
