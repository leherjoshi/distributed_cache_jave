package com.distributedcache.utils;

import com.distributedcache.exceptions.SerializationException;

/**
 * Serializes and deserializes objects for storage and transmission.
 */
public interface MessageSerializer {
    
    /**
     * Serializes an object to bytes.
     * 
     * @param obj the object to serialize
     * @return byte array representation
     * @throws SerializationException if serialization fails
     */
    byte[] serialize(Object obj) throws SerializationException;
    
    /**
     * Deserializes bytes to an object.
     * 
     * @param bytes the byte array
     * @param type the expected type
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    <T> T deserialize(byte[] bytes, Class<T> type) throws SerializationException;
    
    /**
     * Estimates the size of an object in bytes.
     */
    long estimateSize(Object obj);
}
