package com.distributedcache.network;

import com.distributedcache.exceptions.NetworkException;
import com.distributedcache.exceptions.TimeoutException;
import com.distributedcache.hashing.HashRing;
import com.distributedcache.hashing.NodeAddress;
import com.distributedcache.hashing.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Implementation of NetworkClient for sending inter-node messages.
 * Supports retry logic with exponential backoff and broadcast operations.
 */
public class NetworkClientImpl implements NetworkClient {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkClientImpl.class);
    
    // Retry configuration
    private static final int INITIAL_BACKOFF_MS = 100;
    private static final int SECOND_BACKOFF_MS = 200;
    private static final int THIRD_BACKOFF_MS = 400;
    
    // Connection timeout in milliseconds
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    
    private final HashRing hashRing;
    
    /**
     * Creates a new NetworkClientImpl.
     * 
     * @param hashRing the hash ring for getting all nodes (used for broadcast)
     */
    public NetworkClientImpl(HashRing hashRing) {
        this.hashRing = hashRing;
    }
    
    /**
     * Sends a message to a remote node without retry logic.
     * 
     * @param target the target node
     * @param message the message to send
     * @return future containing the response
     */
    @Override
    public <T> CompletableFuture<T> send(NodeInfo target, Message message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendMessageInternal(target, message);
            } catch (Exception e) {
                logger.error("Failed to send message to {}: {}", target, e.getMessage());
                throw new CompletionException(new NetworkException(
                    "Failed to send message to " + target.getNodeId(), e));
            }
        });
    }
    
    /**
     * Sends a message with retry logic and exponential backoff.
     * Retries up to MAX_RETRIES times with backoff delays of 100ms, 200ms, 400ms.
     * 
     * @param target the target node
     * @param message the message to send
     * @return future containing the response
     */
    @Override
    public <T> CompletableFuture<T> sendWithRetry(NodeInfo target, Message message) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;
            
            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                try {
                    if (attempt > 0) {
                        // Apply exponential backoff
                        int backoffMs = getBackoffDelay(attempt);
                        logger.debug("Retry attempt {} for message to {}, backing off {}ms", 
                                   attempt + 1, target.getNodeId(), backoffMs);
                        Thread.sleep(backoffMs);
                    }
                    
                    return sendMessageInternal(target, message);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(new NetworkException(
                        "Interrupted while retrying message to " + target.getNodeId(), e));
                } catch (Exception e) {
                    lastException = e;
                    logger.warn("Attempt {} failed to send message to {}: {}", 
                              attempt + 1, target.getNodeId(), e.getMessage());
                }
            }
            
            // All retries exhausted
            logger.error("All {} retry attempts failed for message to {}", 
                       MAX_RETRIES, target.getNodeId());
            throw new CompletionException(new NetworkException(
                "Failed to send message to " + target.getNodeId() + " after " + 
                MAX_RETRIES + " attempts", lastException));
        });
    }
    
    /**
     * Broadcasts a message to all nodes in the cluster.
     * 
     * @param message the message to broadcast
     * @return future that completes when all broadcasts finish
     */
    @Override
    public CompletableFuture<Void> broadcast(Message message) {
        return CompletableFuture.runAsync(() -> {
            Set<NodeInfo> allNodes = hashRing.getAllNodes();
            
            if (allNodes.isEmpty()) {
                logger.warn("No nodes available for broadcast");
                return;
            }
            
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (NodeInfo node : allNodes) {
                // Skip sending to self (source node)
                if (node.getNodeId().equals(message.getSourceNodeId())) {
                    continue;
                }
                
                CompletableFuture<Void> future = send(node, message)
                    .thenAccept(response -> {
                        logger.debug("Broadcast to {} completed", node.getNodeId());
                    })
                    .exceptionally(ex -> {
                        logger.warn("Broadcast to {} failed: {}", 
                                  node.getNodeId(), ex.getMessage());
                        // Don't fail the entire broadcast if one node fails
                        return null;
                    });
                
                futures.add(future);
            }
            
            // Wait for all broadcasts to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            logger.info("Broadcast completed to {} nodes", futures.size());
        });
    }
    
    /**
     * Internal method to send a message and receive a response.
     * 
     * @param target the target node
     * @param message the message to send
     * @return the response message
     * @throws NetworkException if the send fails
     * @throws TimeoutException if the operation times out
     */
    @SuppressWarnings("unchecked")
    private <T> T sendMessageInternal(NodeInfo target, Message message) 
            throws NetworkException, TimeoutException {
        NodeAddress address = target.getAddress();
        Socket socket = null;
        
        try {
            // Create socket with connection timeout
            socket = new Socket();
            socket.setSoTimeout(READ_TIMEOUT_MS);
            socket.connect(new java.net.InetSocketAddress(address.getHost(), address.getPort()), 
                          CONNECTION_TIMEOUT_MS);
            
            // Serialize message to bytes
            byte[] messageBytes = serializeMessage(message);
            
            // Send message with length prefix (4 bytes for length, then message bytes)
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(messageBytes.length);  // Write length prefix
            out.write(messageBytes);            // Write message bytes
            out.flush();
            
            logger.debug("Sent {} message to {}", message.getType(), target.getNodeId());
            
            // Receive response with length prefix
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int responseLength = in.readInt();  // Read length prefix
            
            if (responseLength <= 0 || responseLength > 10 * 1024 * 1024) {
                throw new NetworkException(
                    "Invalid response length from " + target.getNodeId() + ": " + responseLength);
            }
            
            byte[] responseBytes = new byte[responseLength];
            in.readFully(responseBytes);  // Read response bytes
            
            Object response = deserializeMessage(responseBytes);
            logger.debug("Received response from {}", target.getNodeId());
            return (T) response;
            
        } catch (SocketTimeoutException e) {
            throw new TimeoutException(
                "Timeout while communicating with " + target.getNodeId() + 
                " at " + address, e);
        } catch (IOException e) {
            throw new NetworkException(
                "Network error while communicating with " + target.getNodeId() + 
                " at " + address + ": " + e.getMessage(), e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.warn("Failed to close socket to {}: {}", target.getNodeId(), e.getMessage());
                }
            }
        }
    }
    
    /**
     * Serializes a message to bytes.
     */
    private byte[] serializeMessage(Message message) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
            oos.flush();
            return baos.toByteArray();
        }
    }
    
    /**
     * Deserializes a message from bytes.
     */
    private Message deserializeMessage(byte[] bytes) throws NetworkException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Message) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new NetworkException("Failed to deserialize message", e);
        }
    }
    
    /**
     * Gets the backoff delay for a given retry attempt.
     * 
     * @param attempt the retry attempt number (0-based)
     * @return the backoff delay in milliseconds
     */
    private int getBackoffDelay(int attempt) {
        switch (attempt) {
            case 1:
                return INITIAL_BACKOFF_MS;  // 100ms
            case 2:
                return SECOND_BACKOFF_MS;   // 200ms
            default:
                return THIRD_BACKOFF_MS;    // 400ms
        }
    }
}
