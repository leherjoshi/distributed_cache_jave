package com.distributedcache.hashing;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a network address for a cache node.
 */
public class NodeAddress implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String host;
    private final int port;
    
    public NodeAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeAddress)) return false;
        NodeAddress that = (NodeAddress) o;
        return port == that.port && host.equals(that.host);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
    
    @Override
    public String toString() {
        return host + ":" + port;
    }
}
