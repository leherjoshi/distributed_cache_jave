package com.distributedcache.network;

/**
 * Handler for processing incoming messages.
 */
@FunctionalInterface
public interface MessageHandler {
    
    /**
     * Handles an incoming message.
     * 
     * @param message the received message
     * @return response message, or null if no response needed
     */
    Message handle(Message message);
}
