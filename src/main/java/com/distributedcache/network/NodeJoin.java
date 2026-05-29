package com.distributedcache.network;

import com.distributedcache.hashing.NodeInfo;
import java.io.Serializable;

/**
 * Message broadcast when a node joins the cluster.
 */
public class NodeJoin extends Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final NodeInfo nodeInfo;
    
    public NodeJoin(String sourceNodeId, NodeInfo nodeInfo) {
        super(MessageType.NODE_JOIN, sourceNodeId);
        this.nodeInfo = nodeInfo;
    }
    
    public NodeInfo getNodeInfo() {
        return nodeInfo;
    }
}
