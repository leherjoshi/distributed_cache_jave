package com.distributedcache.hashing;

import java.io.Serializable;
import java.util.Objects;

/**
 * Information about a cache node in the cluster.
 */
public class NodeInfo implements Serializable, Comparable<NodeInfo> {
    private static final long serialVersionUID = 1L;
    
    private final String nodeId;
    private final String host;
    private final int port;
    private final long joinedAt;
    
    public NodeInfo(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.joinedAt = System.currentTimeMillis();
    }
    
    public NodeAddress getAddress() {
        return new NodeAddress(host, port);
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public long getJoinedAt() {
        return joinedAt;
    }
    
    @Override
    public int compareTo(NodeInfo other) {
        return this.nodeId.compareTo(other.nodeId);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeInfo)) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return nodeId.equals(nodeInfo.nodeId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(nodeId);
    }
    
    @Override
    public String toString() {
        return "NodeInfo{" +
                "nodeId='" + nodeId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
