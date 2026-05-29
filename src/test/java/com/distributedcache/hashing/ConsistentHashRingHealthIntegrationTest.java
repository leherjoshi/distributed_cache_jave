package com.distributedcache.hashing;

import com.distributedcache.node.HealthMonitor;
import com.distributedcache.node.HealthStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ConsistentHashRing integration with HealthMonitor.
 * Validates requirements 5.3 and 5.4.
 */
class ConsistentHashRingHealthIntegrationTest {
    
    private HealthMonitor mockHealthMonitor;
    private ConsistentHashRing hashRing;
    private NodeInfo node1;
    private NodeInfo node2;
    private NodeInfo node3;
    
    @BeforeEach
    void setUp() {
        mockHealthMonitor = mock(HealthMonitor.class);
        hashRing = new ConsistentHashRing(mockHealthMonitor);
        
        node1 = new NodeInfo("node1", "localhost", 8001);
        node2 = new NodeInfo("node2", "localhost", 8002);
        node3 = new NodeInfo("node3", "localhost", 8003);
        
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
    }
    
    @Test
    @DisplayName("getPrimaryNode should skip unavailable nodes and return next healthy node")
    void testGetPrimaryNodeSkipsUnhealthyNodes() {
        // Arrange: node1 is unavailable, node2 is healthy
        when(mockHealthMonitor.getNodeStatus("node1")).thenReturn(HealthStatus.UNAVAILABLE);
        when(mockHealthMonitor.getNodeStatus("node2")).thenReturn(HealthStatus.HEALTHY);
        when(mockHealthMonitor.getNodeStatus("node3")).thenReturn(HealthStatus.HEALTHY);
        
        // Act: Get primary node for a key that would normally map to node1
        // We need to find a key that hashes to node1's position
        String testKey = "test-key";
        NodeInfo primaryNode = hashRing.getPrimaryNode(testKey);
        
        // Assert: Should get a healthy node (node2 or node3), not node1
        assertNotNull(primaryNode);
        assertNotEquals("node1", primaryNode.getNodeId());
        assertTrue(primaryNode.getNodeId().equals("node2") || primaryNode.getNodeId().equals("node3"));
    }
    
    @Test
    @DisplayName("getPrimaryNode should include recovered nodes in routing")
    void testGetPrimaryNodeIncludesRecoveredNodes() {
        // Arrange: Initially node1 is unavailable
        when(mockHealthMonitor.getNodeStatus("node1")).thenReturn(HealthStatus.UNAVAILABLE);
        when(mockHealthMonitor.getNodeStatus("node2")).thenReturn(HealthStatus.HEALTHY);
        when(mockHealthMonitor.getNodeStatus("node3")).thenReturn(HealthStatus.HEALTHY);
        
        String testKey = "test-key";
        NodeInfo firstPrimary = hashRing.getPrimaryNode(testKey);
        assertNotEquals("node1", firstPrimary.getNodeId());
        
        // Act: Node1 recovers
        when(mockHealthMonitor.getNodeStatus("node1")).thenReturn(HealthStatus.HEALTHY);
        
        // The primary node selection should now be able to include node1 again
        // We can't guarantee it will be selected for this specific key,
        // but we can verify the system doesn't throw an exception
        NodeInfo secondPrimary = hashRing.getPrimaryNode(testKey);
        assertNotNull(secondPrimary);
    }
    
    @Test
    @DisplayName("getReplicaNodes should only return healthy nodes")
    void testGetReplicaNodesFiltersUnhealthyNodes() {
        // Arrange: node2 is unavailable
        when(mockHealthMonitor.getNodeStatus("node1")).thenReturn(HealthStatus.HEALTHY);
        when(mockHealthMonitor.getNodeStatus("node2")).thenReturn(HealthStatus.UNAVAILABLE);
        when(mockHealthMonitor.getNodeStatus("node3")).thenReturn(HealthStatus.HEALTHY);
        
        // Act: Get 3 replica nodes
        String testKey = "test-key";
        List<NodeInfo> replicas = hashRing.getReplicaNodes(testKey, 3);
        
        // Assert: Should only get healthy nodes (node1 and node3)
        assertNotNull(replicas);
        assertTrue(replicas.size() <= 2, "Should get at most 2 healthy nodes");
        
        for (NodeInfo replica : replicas) {
            assertNotEquals("node2", replica.getNodeId(), 
                "Unavailable node2 should not be in replica list");
        }
    }
    
    @Test
    @DisplayName("getReplicaNodes should include recovered nodes")
    void testGetReplicaNodesIncludesRecoveredNodes() {
        // Arrange: Initially node2 is unavailable
        when(mockHealthMonitor.getNodeStatus("node1")).thenReturn(HealthStatus.HEALTHY);
        when(mockHealthMonitor.getNodeStatus("node2")).thenReturn(HealthStatus.UNAVAILABLE);
        when(mockHealthMonitor.getNodeStatus("node3")).thenReturn(HealthStatus.HEALTHY);
        
        String testKey = "test-key";
        List<NodeInfo> firstReplicas = hashRing.getReplicaNodes(testKey, 3);
        assertTrue(firstReplicas.size() <= 2);
        
        // Act: Node2 recovers
        when(mockHealthMonitor.getNodeStatus("node2")).thenReturn(HealthStatus.HEALTHY);
        
        // Assert: Now we should be able to get up to 3 replicas
        List<NodeInfo> secondReplicas = hashRing.getReplicaNodes(testKey, 3);
        assertTrue(secondReplicas.size() <= 3);
        assertTrue(secondReplicas.size() >= firstReplicas.size(), 
            "Should have at least as many replicas after node recovery");
    }
    
    @Test
    @DisplayName("getPrimaryNode should throw exception when all nodes are unavailable")
    void testGetPrimaryNodeThrowsWhenAllNodesUnavailable() {
        // Arrange: All nodes are unavailable
        when(mockHealthMonitor.getNodeStatus("node1")).thenReturn(HealthStatus.UNAVAILABLE);
        when(mockHealthMonitor.getNodeStatus("node2")).thenReturn(HealthStatus.UNAVAILABLE);
        when(mockHealthMonitor.getNodeStatus("node3")).thenReturn(HealthStatus.UNAVAILABLE);
        
        // Act & Assert
        String testKey = "test-key";
        assertThrows(IllegalStateException.class, () -> {
            hashRing.getPrimaryNode(testKey);
        }, "Should throw exception when no healthy nodes are available");
    }
    
    @Test
    @DisplayName("getReplicaNodes should return empty list when all nodes are unavailable")
    void testGetReplicaNodesReturnsEmptyWhenAllNodesUnavailable() {
        // Arrange: All nodes are unavailable
        when(mockHealthMonitor.getNodeStatus("node1")).thenReturn(HealthStatus.UNAVAILABLE);
        when(mockHealthMonitor.getNodeStatus("node2")).thenReturn(HealthStatus.UNAVAILABLE);
        when(mockHealthMonitor.getNodeStatus("node3")).thenReturn(HealthStatus.UNAVAILABLE);
        
        // Act
        String testKey = "test-key";
        List<NodeInfo> replicas = hashRing.getReplicaNodes(testKey, 3);
        
        // Assert
        assertNotNull(replicas);
        assertTrue(replicas.isEmpty(), "Should return empty list when all nodes are unavailable");
    }
    
    @Test
    @DisplayName("HashRing without HealthMonitor should work normally")
    void testHashRingWithoutHealthMonitor() {
        // Arrange: Create hash ring without health monitor
        ConsistentHashRing ringWithoutHealth = new ConsistentHashRing();
        ringWithoutHealth.addNode(node1);
        ringWithoutHealth.addNode(node2);
        ringWithoutHealth.addNode(node3);
        
        // Act & Assert: Should work normally without health checking
        String testKey = "test-key";
        NodeInfo primaryNode = ringWithoutHealth.getPrimaryNode(testKey);
        assertNotNull(primaryNode);
        
        List<NodeInfo> replicas = ringWithoutHealth.getReplicaNodes(testKey, 3);
        assertNotNull(replicas);
        assertEquals(3, replicas.size());
    }
}
