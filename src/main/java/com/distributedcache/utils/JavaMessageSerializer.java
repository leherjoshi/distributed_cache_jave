package com.distributedcache.utils;

import com.distributedcache.exceptions.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Implementation of MessageSerializer using Java's built-in serialization.
 * Supports serialization of objects implementing the Serializable interface.
 */
public class JavaMessageSerializer implements MessageSerializer {
    
    private static final Logger logger = LoggerFactory.getLogger(JavaMessageSerializer.class);
    
    /**
     * Serializes an object to a byte array using Java serialization.
     * 
     * @param obj the object to serialize (must implement Serializable)
     * @return byte array representation of the object
     * @throws SerializationException if the object is not serializable or serialization fails
     */
    @Override
    public byte[] serialize(Object obj) throws SerializationException {
        if (obj == null) {
            throw new SerializationException("Cannot serialize null object");
        }
        
        if (!(obj instanceof Serializable)) {
            throw new SerializationException(
                String.format("Object of type %s does not implement Serializable interface", 
                    obj.getClass().getName())
            );
        }
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            
            oos.writeObject(obj);
            oos.flush();
            byte[] result = baos.toByteArray();
            
            logger.debug("Serialized object of type {} to {} bytes", 
                obj.getClass().getName(), result.length);
            
            return result;
            
        } catch (NotSerializableException e) {
            throw new SerializationException(
                String.format("Object of type %s contains non-serializable fields: %s", 
                    obj.getClass().getName(), e.getMessage()),
                e
            );
        } catch (IOException e) {
            throw new SerializationException(
                String.format("Failed to serialize object of type %s: %s", 
                    obj.getClass().getName(), e.getMessage()),
                e
            );
        }
    }
    
    /**
     * Deserializes a byte array to an object using Java deserialization.
     * 
     * @param bytes the byte array to deserialize
     * @param type the expected type of the deserialized object
     * @return the deserialized object
     * @throws SerializationException if deserialization fails or type mismatch occurs
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) throws SerializationException {
        if (bytes == null) {
            throw new SerializationException("Cannot deserialize null byte array");
        }
        
        if (bytes.length == 0) {
            throw new SerializationException("Cannot deserialize empty byte array");
        }
        
        if (type == null) {
            throw new SerializationException("Target type cannot be null");
        }
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            
            Object obj = ois.readObject();
            
            if (!type.isInstance(obj)) {
                throw new SerializationException(
                    String.format("Deserialized object type %s does not match expected type %s",
                        obj.getClass().getName(), type.getName())
                );
            }
            
            logger.debug("Deserialized {} bytes to object of type {}", 
                bytes.length, type.getName());
            
            return type.cast(obj);
            
        } catch (ClassNotFoundException e) {
            throw new SerializationException(
                String.format("Class not found during deserialization: %s", e.getMessage()),
                e
            );
        } catch (InvalidClassException e) {
            throw new SerializationException(
                String.format("Invalid class during deserialization: %s", e.getMessage()),
                e
            );
        } catch (StreamCorruptedException e) {
            throw new SerializationException(
                "Corrupted stream data during deserialization",
                e
            );
        } catch (IOException e) {
            throw new SerializationException(
                String.format("Failed to deserialize to type %s: %s", 
                    type.getName(), e.getMessage()),
                e
            );
        }
    }
    
    /**
     * Estimates the size of an object in bytes by serializing it.
     * This is an accurate measurement but has performance overhead.
     * 
     * @param obj the object to estimate size for
     * @return estimated size in bytes, or 0 if estimation fails
     */
    @Override
    public long estimateSize(Object obj) {
        if (obj == null) {
            return 0;
        }
        
        if (!(obj instanceof Serializable)) {
            logger.warn("Cannot estimate size of non-serializable object of type {}", 
                obj.getClass().getName());
            return 0;
        }
        
        try {
            byte[] serialized = serialize(obj);
            return serialized.length;
        } catch (SerializationException e) {
            logger.warn("Failed to estimate size for object of type {}: {}", 
                obj.getClass().getName(), e.getMessage());
            return 0;
        }
    }
}
