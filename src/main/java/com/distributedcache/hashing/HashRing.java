package com.distributedcache.hashing;

import java.util.List;
import java.util.Set;

/**
 * Consistent hash ring for distributing keys across cache nodes.
 * Uses virtual nodes to improve load distribution.
 */
public interface HashRing {
    
    /**
     * Number of virtual nodes per physical node.
     */
    int VIRTUAL_NODES_PER_NODE = 150;
    
    /**
     * Adds a node to the hash ring.
     * 
     * @param node the node to add
     */
    void addNode(NodeInfo node);
    
    /**
     * Removes a node from the hash ring.
     * 
     * @param nodeId the ID of the node to remove
     * @return set of keys that need to be redistributed
     */
    Set<String> removeNode(String nodeId);
    
    /**
     * Gets the primary node responsible for a key.
     * 
     * @param key the cache key
     * @return the primary node for this key
     */
    NodeInfo getPrimaryNode(String key);
    
    /**
     * Gets the replica nodes for a key.
     * 
     * @param key the cache key
     * @param replicationFactor number of replicas (including primary)
     * @return list of nodes in preference order (primary first)
     */
    List<NodeInfo> getReplicaNodes(String key, int replicationFactor);
    
    /**
     * Gets all nodes currently in the ring.
     */
    Set<NodeInfo> getAllNodes();
    
    /**
     * Gets the hash value for a key.
     */
    long hash(String key);
}
