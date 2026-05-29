package com.distributedcache.network;

import com.distributedcache.hashing.NodeInfo;
import java.util.concurrent.CompletableFuture;

/**
 * Client component for sending inter-node messages.
 */
public interface NetworkClient {
    
    /**
     * Maximum retry attempts for failed transmissions.
     */
    int MAX_RETRIES = 3;
    
    /**
     * Expected message delivery time under normal conditions.
     */
    long EXPECTED_LATENCY_MS = 20;
    
    /**
     * Sends a message to a remote node.
     * 
     * @param target the target node
     * @param message the message to send
     * @return future containing the response
     */
    <T> CompletableFuture<T> send(NodeInfo target, Message message);
    
    /**
     * Sends a message with retry logic and exponential backoff.
     */
    <T> CompletableFuture<T> sendWithRetry(NodeInfo target, Message message);
    
    /**
     * Broadcasts a message to all nodes.
     */
    CompletableFuture<Void> broadcast(Message message);
}
