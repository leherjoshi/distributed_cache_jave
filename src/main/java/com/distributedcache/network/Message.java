package com.distributedcache.network;

import java.io.Serializable;
import java.util.UUID;

/**
 * Base class for inter-node messages.
 */
public abstract class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String messageId;
    private final MessageType type;
    private final String sourceNodeId;
    private final long timestamp;
    
    protected Message(MessageType type, String sourceNodeId) {
        this.messageId = UUID.randomUUID().toString();
        this.type = type;
        this.sourceNodeId = sourceNodeId;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public String getSourceNodeId() {
        return sourceNodeId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
}
