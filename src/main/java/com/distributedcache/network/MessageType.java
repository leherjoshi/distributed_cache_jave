package com.distributedcache.network;

/**
 * Message types for inter-node communication.
 */
public enum MessageType {
    GET_REQUEST,
    GET_RESPONSE,
    PUT_REQUEST,
    PUT_RESPONSE,
    DELETE_REQUEST,
    DELETE_RESPONSE,
    REPLICATE_REQUEST,
    HEALTH_CHECK,
    HEALTH_RESPONSE,
    NODE_JOIN,
    NODE_LEAVE,
    REBALANCE
}
