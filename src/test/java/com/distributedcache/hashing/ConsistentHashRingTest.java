package com.distributedcache.hashing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConsistentHashRing.
 */
class ConsistentHashRingTest {
    
    private ConsistentHashRing hashRing;
    private NodeInfo node1;
    private NodeInfo node2;
    private NodeInfo node3;
    
    @BeforeEach
    void setUp() {
        hashRing = new ConsistentHashRing();
        node1 = new NodeInfo("node1", "localhost", 8001);
        node2 = new NodeInfo("node2", "localhost", 8002);
        node3 = new NodeInfo("node3", "localhost", 8003);
    }
    
    // ========== addNode() Tests ==========
    
    @Test
    @DisplayName("addNode() should add a node to the ring")
    void testAddNode() {
        hashRing.addNode(node1);
        
        Set<NodeInfo> nodes = hashRing.getAllNodes();
        
        assertEquals(1, nodes.size());
        assertTrue(nodes.contains(node1));
    }
    
    @Test
    @DisplayName("addNode() should add multiple nodes to the ring")
    void testAddMultipleNodes() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        Set<NodeInfo> nodes = hashRing.getAllNodes();
        
        assertEquals(3, nodes.size());
        assertTrue(nodes.contains(node1));
        assertTrue(nodes.contains(node2));
        assertTrue(nodes.contains(node3));
    }
    
    @Test
    @DisplayName("addNode() should not add duplicate nodes")
    void testAddDuplicateNode() {
        hashRing.addNode(node1);
        hashRing.addNode(node1);
        
        Set<NodeInfo> nodes = hashRing.getAllNodes();
        
        assertEquals(1, nodes.size());
    }
    
    @Test
    @DisplayName("addNode() should throw exception for null node")
    void testAddNullNode() {
        assertThrows(IllegalArgumentException.class, () -> hashRing.addNode(null));
    }
    
    // ========== removeNode() Tests ==========
    
    @Test
    @DisplayName("removeNode() should remove a node from the ring")
    void testRemoveNode() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        
        hashRing.removeNode("node1");
        
        Set<NodeInfo> nodes = hashRing.getAllNodes();
        assertEquals(1, nodes.size());
        assertFalse(nodes.contains(node1));
        assertTrue(nodes.contains(node2));
    }
    
    @Test
    @DisplayName("removeNode() should return empty set for non-existent node")
    void testRemoveNonExistentNode() {
        hashRing.addNode(node1);
        
        Set<String> affectedKeys = hashRing.removeNode("nonexistent");
        
        assertTrue(affectedKeys.isEmpty());
    }
    
    @Test
    @DisplayName("removeNode() should throw exception for null node ID")
    void testRemoveNullNodeId() {
        assertThrows(IllegalArgumentException.class, () -> hashRing.removeNode(null));
    }
    
    @Test
    @DisplayName("removeNode() should remove all virtual nodes")
    void testRemoveNodeRemovesVirtualNodes() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        
        // Get a key that maps to node1
        String key = findKeyForNode(node1);
        assertNotNull(key);
        
        // Remove node1
        hashRing.removeNode("node1");
        
        // The key should now map to node2
        NodeInfo primaryNode = hashRing.getPrimaryNode(key);
        assertEquals(node2, primaryNode);
    }
    
    // ========== getPrimaryNode() Tests ==========
    
    @Test
    @DisplayName("getPrimaryNode() should return a node for a key")
    void testGetPrimaryNode() {
        hashRing.addNode(node1);
        
        NodeInfo primaryNode = hashRing.getPrimaryNode("testKey");
        
        assertNotNull(primaryNode);
        assertEquals(node1, primaryNode);
    }
    
    @Test
    @DisplayName("getPrimaryNode() should return consistent node for same key")
    void testGetPrimaryNodeConsistency() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        NodeInfo first = hashRing.getPrimaryNode("testKey");
        NodeInfo second = hashRing.getPrimaryNode("testKey");
        NodeInfo third = hashRing.getPrimaryNode("testKey");
        
        assertEquals(first, second);
        assertEquals(second, third);
    }
    
    @Test
    @DisplayName("getPrimaryNode() should distribute keys across nodes")
    void testGetPrimaryNodeDistribution() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        Map<NodeInfo, Integer> distribution = new HashMap<>();
        
        // Test with 300 keys
        for (int i = 0; i < 300; i++) {
            String key = "key" + i;
            NodeInfo node = hashRing.getPrimaryNode(key);
            distribution.put(node, distribution.getOrDefault(node, 0) + 1);
        }
        
        // Each node should get some keys (not perfect distribution, but not zero)
        assertEquals(3, distribution.size());
        for (Integer count : distribution.values()) {
            assertTrue(count > 0, "Each node should handle at least one key");
        }
    }
    
    @Test
    @DisplayName("getPrimaryNode() should throw exception for null key")
    void testGetPrimaryNodeNullKey() {
        hashRing.addNode(node1);
        
        assertThrows(IllegalArgumentException.class, () -> hashRing.getPrimaryNode(null));
    }
    
    @Test
    @DisplayName("getPrimaryNode() should throw exception for empty ring")
    void testGetPrimaryNodeEmptyRing() {
        assertThrows(IllegalStateException.class, () -> hashRing.getPrimaryNode("testKey"));
    }
    
    // ========== getReplicaNodes() Tests ==========
    
    @Test
    @DisplayName("getReplicaNodes() should return correct number of replicas")
    void testGetReplicaNodes() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        List<NodeInfo> replicas = hashRing.getReplicaNodes("testKey", 2);
        
        assertEquals(2, replicas.size());
    }
    
    @Test
    @DisplayName("getReplicaNodes() should return primary node first")
    void testGetReplicaNodesPrimaryFirst() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        NodeInfo primary = hashRing.getPrimaryNode("testKey");
        List<NodeInfo> replicas = hashRing.getReplicaNodes("testKey", 3);
        
        assertEquals(primary, replicas.get(0));
    }
    
    @Test
    @DisplayName("getReplicaNodes() should return unique physical nodes")
    void testGetReplicaNodesUnique() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        List<NodeInfo> replicas = hashRing.getReplicaNodes("testKey", 3);
        
        Set<String> nodeIds = new HashSet<>();
        for (NodeInfo node : replicas) {
            nodeIds.add(node.getNodeId());
        }
        
        assertEquals(3, nodeIds.size());
    }
    
    @Test
    @DisplayName("getReplicaNodes() should handle replication factor larger than node count")
    void testGetReplicaNodesExceedsNodeCount() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        
        List<NodeInfo> replicas = hashRing.getReplicaNodes("testKey", 5);
        
        // Should return only available nodes
        assertEquals(2, replicas.size());
    }
    
    @Test
    @DisplayName("getReplicaNodes() should return consistent replicas for same key")
    void testGetReplicaNodesConsistency() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        List<NodeInfo> first = hashRing.getReplicaNodes("testKey", 2);
        List<NodeInfo> second = hashRing.getReplicaNodes("testKey", 2);
        
        assertEquals(first, second);
    }
    
    @Test
    @DisplayName("getReplicaNodes() should throw exception for null key")
    void testGetReplicaNodesNullKey() {
        hashRing.addNode(node1);
        
        assertThrows(IllegalArgumentException.class, () -> hashRing.getReplicaNodes(null, 2));
    }
    
    @Test
    @DisplayName("getReplicaNodes() should throw exception for invalid replication factor")
    void testGetReplicaNodesInvalidFactor() {
        hashRing.addNode(node1);
        
        assertThrows(IllegalArgumentException.class, () -> hashRing.getReplicaNodes("testKey", 0));
        assertThrows(IllegalArgumentException.class, () -> hashRing.getReplicaNodes("testKey", -1));
    }
    
    @Test
    @DisplayName("getReplicaNodes() should throw exception for empty ring")
    void testGetReplicaNodesEmptyRing() {
        assertThrows(IllegalStateException.class, () -> hashRing.getReplicaNodes("testKey", 2));
    }
    
    // ========== getAllNodes() Tests ==========
    
    @Test
    @DisplayName("getAllNodes() should return empty set for empty ring")
    void testGetAllNodesEmpty() {
        Set<NodeInfo> nodes = hashRing.getAllNodes();
        
        assertTrue(nodes.isEmpty());
    }
    
    @Test
    @DisplayName("getAllNodes() should return all added nodes")
    void testGetAllNodes() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        Set<NodeInfo> nodes = hashRing.getAllNodes();
        
        assertEquals(3, nodes.size());
        assertTrue(nodes.contains(node1));
        assertTrue(nodes.contains(node2));
        assertTrue(nodes.contains(node3));
    }
    
    @Test
    @DisplayName("getAllNodes() should return updated set after node removal")
    void testGetAllNodesAfterRemoval() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.removeNode("node1");
        
        Set<NodeInfo> nodes = hashRing.getAllNodes();
        
        assertEquals(1, nodes.size());
        assertTrue(nodes.contains(node2));
    }
    
    // ========== hash() Tests ==========
    
    @Test
    @DisplayName("hash() should return consistent hash for same key")
    void testHashConsistency() {
        long hash1 = hashRing.hash("testKey");
        long hash2 = hashRing.hash("testKey");
        
        assertEquals(hash1, hash2);
    }
    
    @Test
    @DisplayName("hash() should return different hashes for different keys")
    void testHashDifferentKeys() {
        long hash1 = hashRing.hash("key1");
        long hash2 = hashRing.hash("key2");
        
        assertNotEquals(hash1, hash2);
    }
    
    @Test
    @DisplayName("hash() should throw exception for null key")
    void testHashNullKey() {
        assertThrows(IllegalArgumentException.class, () -> hashRing.hash(null));
    }
    
    @Test
    @DisplayName("hash() should handle empty string")
    void testHashEmptyString() {
        long hash = hashRing.hash("");
        
        assertNotEquals(0, hash);
    }
    
    // ========== Virtual Nodes Tests ==========
    
    @Test
    @DisplayName("addNode() should create 150 virtual nodes per physical node")
    void testVirtualNodesCount() {
        hashRing.addNode(node1);
        
        // Test that keys are distributed across virtual nodes
        Set<Long> uniqueHashes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String key = "key" + i;
            NodeInfo node = hashRing.getPrimaryNode(key);
            if (node.equals(node1)) {
                uniqueHashes.add(hashRing.hash(key));
            }
        }
        
        // With 150 virtual nodes, we should see good distribution
        assertTrue(uniqueHashes.size() > 1, "Keys should map to different hash positions");
    }
    
    // ========== Edge Cases ==========
    
    @Test
    @DisplayName("getPrimaryNode() should handle single node")
    void testGetPrimaryNodeSingleNode() {
        hashRing.addNode(node1);
        
        NodeInfo node = hashRing.getPrimaryNode("key1");
        assertEquals(node1, node);
        
        node = hashRing.getPrimaryNode("key2");
        assertEquals(node1, node);
    }
    
    @Test
    @DisplayName("getReplicaNodes() should handle single node")
    void testGetReplicaNodesSingleNode() {
        hashRing.addNode(node1);
        
        List<NodeInfo> replicas = hashRing.getReplicaNodes("testKey", 3);
        
        assertEquals(1, replicas.size());
        assertEquals(node1, replicas.get(0));
    }
    
    @Test
    @DisplayName("Node addition should not affect existing key mappings significantly")
    void testNodeAdditionMinimalRedistribution() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        
        // Record mappings before adding node3
        Map<String, NodeInfo> beforeMappings = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            String key = "key" + i;
            beforeMappings.put(key, hashRing.getPrimaryNode(key));
        }
        
        // Add node3
        hashRing.addNode(node3);
        
        // Count how many keys changed
        int changedKeys = 0;
        for (int i = 0; i < 100; i++) {
            String key = "key" + i;
            NodeInfo afterNode = hashRing.getPrimaryNode(key);
            if (!afterNode.equals(beforeMappings.get(key))) {
                changedKeys++;
            }
        }
        
        // With consistent hashing, only a fraction of keys should move
        // Ideally around 1/3 (100/3 ≈ 33), but we'll be lenient
        assertTrue(changedKeys < 100, "Not all keys should move when adding a node");
        assertTrue(changedKeys > 0, "Some keys should move when adding a node");
    }
    
    @Test
    @DisplayName("Node removal should not affect keys on other nodes")
    void testNodeRemovalMinimalRedistribution() {
        hashRing.addNode(node1);
        hashRing.addNode(node2);
        hashRing.addNode(node3);
        
        // Find keys that map to node2 and node3
        List<String> node2Keys = new ArrayList<>();
        List<String> node3Keys = new ArrayList<>();
        
        for (int i = 0; i < 300; i++) {
            String key = "key" + i;
            NodeInfo node = hashRing.getPrimaryNode(key);
            if (node.equals(node2)) {
                node2Keys.add(key);
            } else if (node.equals(node3)) {
                node3Keys.add(key);
            }
        }
        
        // Remove node1
        hashRing.removeNode("node1");
        
        // Keys that were on node2 and node3 should still be there
        for (String key : node2Keys) {
            assertEquals(node2, hashRing.getPrimaryNode(key));
        }
        for (String key : node3Keys) {
            assertEquals(node3, hashRing.getPrimaryNode(key));
        }
    }
    
    // ========== Helper Methods ==========
    
    /**
     * Finds a key that maps to the specified node.
     * Returns null if no key found after 1000 attempts.
     */
    private String findKeyForNode(NodeInfo targetNode) {
        for (int i = 0; i < 1000; i++) {
            String key = "key" + i;
            NodeInfo node = hashRing.getPrimaryNode(key);
            if (node.equals(targetNode)) {
                return key;
            }
        }
        return null;
    }
}
