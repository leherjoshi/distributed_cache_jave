package com.distributedcache.network;

import com.distributedcache.exceptions.NetworkException;

/**
 * Server component for receiving inter-node messages.
 */
public interface NetworkServer extends AutoCloseable {
    
    /**
     * Starts the network server on the configured port.
     */
    void start() throws NetworkException;
    
    /**
     * Stops the network server.
     */
    void stop() throws NetworkException;
    
    /**
     * Registers a handler for incoming messages.
     */
    void registerHandler(MessageType type, MessageHandler handler);
    
    /**
     * Gets the port the server is listening on.
     */
    int getPort();
}
