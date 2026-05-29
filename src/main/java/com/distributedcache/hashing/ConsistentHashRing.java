package com.distributedcache.hashing;

import com.distributedcache.node.HealthMonitor;
import com.distributedcache.node.HealthStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of consistent hashing using a TreeMap-based hash ring.
 * Uses virtual nodes to improve load distribution across physical nodes.
 * Integrates with HealthMonitor to filter out unavailable nodes.
 */
public class ConsistentHashRing implements HashRing {
    
    private final TreeMap<Long, NodeInfo> ring;
    private final Map<String, Set<Long>> nodeToHashes;
    private final Map<String, NodeInfo> nodes;
    private final ReadWriteLock lock;
    private final MessageDigest digest;
    private final HealthMonitor healthMonitor;
    
    /**
     * Creates a new ConsistentHashRing without health monitoring.
     * This constructor is for backward compatibility and testing.
     */
    public ConsistentHashRing() {
        this(null);
    }
    
    /**
     * Creates a new ConsistentHashRing with health monitoring.
     * 
     * @param healthMonitor the health monitor to check node availability (can be null)
     */
    public ConsistentHashRing(HealthMonitor healthMonitor) {
        this.ring = new TreeMap<>();
        this.nodeToHashes = new ConcurrentHashMap<>();
        this.nodes = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.healthMonitor = healthMonitor;
        
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    @Override
    public void addNode(NodeInfo node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        
        lock.writeLock().lock();
        try {
            String nodeId = node.getNodeId();
            
            // Skip if node already exists
            if (nodes.containsKey(nodeId)) {
                return;
            }
            
            nodes.put(nodeId, node);
            Set<Long> hashes = new HashSet<>();
            
            // Create virtual nodes
            for (int i = 0; i < VIRTUAL_NODES_PER_NODE; i++) {
                String virtualNodeId = nodeId + "#" + i;
                long hash = hash(virtualNodeId);
                ring.put(hash, node);
                hashes.add(hash);
            }
            
            nodeToHashes.put(nodeId, hashes);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Set<String> removeNode(String nodeId) {
        if (nodeId == null) {
            throw new IllegalArgumentException("Node ID cannot be null");
        }
        
        lock.writeLock().lock();
        try {
            Set<Long> hashes = nodeToHashes.remove(nodeId);
            nodes.remove(nodeId);
            
            if (hashes == null) {
                return Collections.emptySet();
            }
            
            // Remove all virtual nodes from the ring
            for (Long hash : hashes) {
                ring.remove(hash);
            }
            
            // In a real implementation, we would track which keys were assigned to this node
            // For now, return an empty set as key tracking is not part of this task
            return Collections.emptySet();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public NodeInfo getPrimaryNode(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                throw new IllegalStateException("Hash ring is empty");
            }
            
            long keyHash = hash(key);
            
            // Find the first healthy node with hash >= keyHash
            Map.Entry<Long, NodeInfo> entry = ring.ceilingEntry(keyHash);
            
            // If no node found, wrap around to the beginning
            if (entry == null) {
                entry = ring.firstEntry();
            }
            
            // If health monitor is not configured, return the node directly
            if (healthMonitor == null) {
                return entry.getValue();
            }
            
            // Find the first healthy node starting from the entry position
            NodeInfo node = findNextHealthyNode(entry.getKey());
            
            if (node == null) {
                throw new IllegalStateException("No healthy nodes available in the ring");
            }
            
            return node;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public List<NodeInfo> getReplicaNodes(String key, int replicationFactor) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (replicationFactor < 1) {
            throw new IllegalArgumentException("Replication factor must be at least 1");
        }
        
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                throw new IllegalStateException("Hash ring is empty");
            }
            
            List<NodeInfo> replicas = new ArrayList<>();
            Set<String> seenNodeIds = new HashSet<>();
            
            long keyHash = hash(key);
            
            // Start from the primary node position
            Map.Entry<Long, NodeInfo> entry = ring.ceilingEntry(keyHash);
            if (entry == null) {
                entry = ring.firstEntry();
            }
            
            // If health monitor is not configured, use original logic
            if (healthMonitor == null) {
                return getReplicaNodesWithoutHealthCheck(entry, replicationFactor, seenNodeIds);
            }
            
            // Collect unique healthy physical nodes in ring order
            Iterator<Map.Entry<Long, NodeInfo>> iterator = ring.tailMap(entry.getKey()).entrySet().iterator();
            boolean wrapped = false;
            
            while (replicas.size() < replicationFactor && replicas.size() < nodes.size()) {
                if (!iterator.hasNext()) {
                    if (wrapped) {
                        break; // We've gone through the entire ring
                    }
                    // Wrap around to the beginning
                    iterator = ring.entrySet().iterator();
                    wrapped = true;
                }
                
                if (iterator.hasNext()) {
                    Map.Entry<Long, NodeInfo> current = iterator.next();
                    NodeInfo node = current.getValue();
                    String nodeId = node.getNodeId();
                    
                    // Only add unique physical nodes that are healthy
                    if (!seenNodeIds.contains(nodeId) && isNodeHealthy(node)) {
                        replicas.add(node);
                        seenNodeIds.add(nodeId);
                    }
                }
            }
            
            return replicas;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets replica nodes without health checking (for backward compatibility).
     * This method is called when healthMonitor is null.
     */
    private List<NodeInfo> getReplicaNodesWithoutHealthCheck(Map.Entry<Long, NodeInfo> startEntry, 
                                                              int replicationFactor, 
                                                              Set<String> seenNodeIds) {
        List<NodeInfo> replicas = new ArrayList<>();
        
        // Collect unique physical nodes in ring order
        Iterator<Map.Entry<Long, NodeInfo>> iterator = ring.tailMap(startEntry.getKey()).entrySet().iterator();
        boolean wrapped = false;
        
        while (replicas.size() < replicationFactor && replicas.size() < nodes.size()) {
            if (!iterator.hasNext()) {
                if (wrapped) {
                    break; // We've gone through the entire ring
                }
                // Wrap around to the beginning
                iterator = ring.entrySet().iterator();
                wrapped = true;
            }
            
            if (iterator.hasNext()) {
                Map.Entry<Long, NodeInfo> current = iterator.next();
                NodeInfo node = current.getValue();
                String nodeId = node.getNodeId();
                
                // Only add unique physical nodes
                if (!seenNodeIds.contains(nodeId)) {
                    replicas.add(node);
                    seenNodeIds.add(nodeId);
                }
            }
        }
        
        return replicas;
    }
    
    @Override
    public Set<NodeInfo> getAllNodes() {
        lock.readLock().lock();
        try {
            return new HashSet<>(nodes.values());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public long hash(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        
        synchronized (digest) {
            digest.reset();
            byte[] hashBytes = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            
            // Use the first 8 bytes to create a long value
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (hashBytes[i] & 0xFF);
            }
            
            return hash;
        }
    }
    
    /**
     * Checks if a node is healthy according to the health monitor.
     * Requirement 5.3: Exclude unavailable nodes from routing
     * 
     * @param node the node to check
     * @return true if the node is healthy or health monitor is not configured
     */
    private boolean isNodeHealthy(NodeInfo node) {
        if (healthMonitor == null) {
            return true; // No health monitoring, assume healthy
        }
        
        HealthStatus status = healthMonitor.getNodeStatus(node.getNodeId());
        return status == HealthStatus.HEALTHY;
    }
    
    /**
     * Finds the next healthy node in the ring starting from the given hash position.
     * Requirement 5.3: Exclude unavailable nodes from routing
     * Requirement 5.4: Include recovered nodes in routing
     * 
     * @param startHash the hash position to start searching from
     * @return the next healthy node, or null if no healthy nodes are available
     */
    private NodeInfo findNextHealthyNode(long startHash) {
        // Start from the given position and search forward
        Map.Entry<Long, NodeInfo> entry = ring.ceilingEntry(startHash);
        
        Set<String> checkedNodes = new HashSet<>();
        boolean wrapped = false;
        
        while (checkedNodes.size() < nodes.size()) {
            if (entry == null) {
                if (wrapped) {
                    break; // We've checked all nodes
                }
                // Wrap around to the beginning
                entry = ring.firstEntry();
                wrapped = true;
            }
            
            if (entry != null) {
                NodeInfo node = entry.getValue();
                String nodeId = node.getNodeId();
                
                // Check if we've already checked this physical node
                if (!checkedNodes.contains(nodeId)) {
                    checkedNodes.add(nodeId);
                    
                    // Return the first healthy node we find
                    if (isNodeHealthy(node)) {
                        return node;
                    }
                }
                
                // Move to the next entry in the ring
                entry = ring.higherEntry(entry.getKey());
            }
        }
        
        // No healthy nodes found
        return null;
    }
}
