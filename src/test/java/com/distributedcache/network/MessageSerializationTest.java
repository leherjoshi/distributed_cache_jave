package com.distributedcache.network;

import com.distributedcache.hashing.NodeInfo;
import com.distributedcache.node.HealthStatus;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for message serialization and deserialization.
 */
class MessageSerializationTest {
    
    private static final String TEST_NODE_ID = "node-1";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-value";
    
    @Test
    void testGetRequestSerialization() throws Exception {
        GetRequest original = new GetRequest(TEST_NODE_ID, TEST_KEY);
        
        GetRequest deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertEquals(MessageType.GET_REQUEST, deserialized.getType());
    }
    
    @Test
    void testGetResponseSerializationWithValue() throws Exception {
        GetResponse original = new GetResponse(TEST_NODE_ID, TEST_KEY, TEST_VALUE);
        
        GetResponse deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertTrue(deserialized.isFound());
        assertEquals(TEST_VALUE, deserialized.getValue().orElse(null));
        assertEquals(MessageType.GET_RESPONSE, deserialized.getType());
    }
    
    @Test
    void testGetResponseSerializationWithoutValue() throws Exception {
        GetResponse original = new GetResponse(TEST_NODE_ID, TEST_KEY, null);
        
        GetResponse deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertFalse(deserialized.isFound());
        assertEquals(Optional.empty(), deserialized.getValue());
    }
    
    @Test
    void testPutRequestSerialization() throws Exception {
        PutRequest original = new PutRequest(TEST_NODE_ID, TEST_KEY, TEST_VALUE);
        
        PutRequest deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertEquals(original.getValue(), deserialized.getValue());
        assertEquals(MessageType.PUT_REQUEST, deserialized.getType());
    }
    
    @Test
    void testPutResponseSerialization() throws Exception {
        PutResponse original = new PutResponse(TEST_NODE_ID, TEST_KEY, true);
        
        PutResponse deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertTrue(deserialized.isSuccess());
        assertEquals(MessageType.PUT_RESPONSE, deserialized.getType());
    }
    
    @Test
    void testDeleteRequestSerialization() throws Exception {
        DeleteRequest original = new DeleteRequest(TEST_NODE_ID, TEST_KEY);
        
        DeleteRequest deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertEquals(MessageType.DELETE_REQUEST, deserialized.getType());
    }
    
    @Test
    void testDeleteResponseSerialization() throws Exception {
        DeleteResponse original = new DeleteResponse(TEST_NODE_ID, TEST_KEY, true);
        
        DeleteResponse deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertTrue(deserialized.isSuccess());
        assertEquals(MessageType.DELETE_RESPONSE, deserialized.getType());
    }
    
    @Test
    void testReplicateRequestSerializationWithValue() throws Exception {
        ReplicateRequest original = new ReplicateRequest(TEST_NODE_ID, TEST_KEY, TEST_VALUE);
        
        ReplicateRequest deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertEquals(original.getValue(), deserialized.getValue());
        assertFalse(deserialized.isDelete());
        assertEquals(MessageType.REPLICATE_REQUEST, deserialized.getType());
    }
    
    @Test
    void testReplicateRequestSerializationForDelete() throws Exception {
        ReplicateRequest original = new ReplicateRequest(TEST_NODE_ID, TEST_KEY);
        
        ReplicateRequest deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getKey(), deserialized.getKey());
        assertTrue(deserialized.isDelete());
        assertNull(deserialized.getValue());
    }
    
    @Test
    void testHealthCheckSerialization() throws Exception {
        HealthCheck original = new HealthCheck(TEST_NODE_ID);
        
        HealthCheck deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(MessageType.HEALTH_CHECK, deserialized.getType());
    }
    
    @Test
    void testHealthResponseSerialization() throws Exception {
        HealthResponse original = new HealthResponse(
            TEST_NODE_ID, 
            HealthStatus.HEALTHY, 
            1024 * 1024, 
            10 * 1024 * 1024
        );
        
        HealthResponse deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(HealthStatus.HEALTHY, deserialized.getStatus());
        assertEquals(1024 * 1024, deserialized.getMemoryUsageBytes());
        assertEquals(10 * 1024 * 1024, deserialized.getCapacityBytes());
        assertEquals(0.1, deserialized.getMemoryUsagePercentage(), 0.001);
        assertEquals(MessageType.HEALTH_RESPONSE, deserialized.getType());
    }
    
    @Test
    void testNodeJoinSerialization() throws Exception {
        NodeInfo nodeInfo = new NodeInfo("node-2", "localhost", 8080);
        NodeJoin original = new NodeJoin(TEST_NODE_ID, nodeInfo);
        
        NodeJoin deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertNotNull(deserialized.getNodeInfo());
        assertEquals("node-2", deserialized.getNodeInfo().getNodeId());
        assertEquals("localhost", deserialized.getNodeInfo().getHost());
        assertEquals(8080, deserialized.getNodeInfo().getPort());
        assertEquals(MessageType.NODE_JOIN, deserialized.getType());
    }
    
    @Test
    void testNodeLeaveSerialization() throws Exception {
        NodeLeave original = new NodeLeave(TEST_NODE_ID, "node-2");
        
        NodeLeave deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals("node-2", deserialized.getLeavingNodeId());
        assertEquals(MessageType.NODE_LEAVE, deserialized.getType());
    }
    
    @Test
    void testRebalanceSerialization() throws Exception {
        List<String> affectedKeys = Arrays.asList("key1", "key2", "key3");
        Rebalance original = new Rebalance(TEST_NODE_ID, affectedKeys, "node-2");
        
        Rebalance deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
        assertEquals(affectedKeys, deserialized.getAffectedKeys());
        assertEquals("node-2", deserialized.getTargetNodeId());
        assertEquals(MessageType.REBALANCE, deserialized.getType());
    }
    
    @Test
    void testMessageTimestampIsPreserved() throws Exception {
        GetRequest original = new GetRequest(TEST_NODE_ID, TEST_KEY);
        long originalTimestamp = original.getTimestamp();
        
        // Add a small delay to ensure timestamp would be different if regenerated
        Thread.sleep(10);
        
        GetRequest deserialized = serializeAndDeserialize(original);
        
        assertEquals(originalTimestamp, deserialized.getTimestamp());
    }
    
    @Test
    void testComplexValueSerialization() throws Exception {
        // Test with a complex serializable object
        ComplexValue complexValue = new ComplexValue("data", 42);
        PutRequest original = new PutRequest(TEST_NODE_ID, TEST_KEY, complexValue);
        
        PutRequest deserialized = serializeAndDeserialize(original);
        
        assertNotNull(deserialized);
        ComplexValue deserializedValue = (ComplexValue) deserialized.getValue();
        assertEquals("data", deserializedValue.data);
        assertEquals(42, deserializedValue.number);
    }
    
    /**
     * Helper method to serialize and deserialize an object.
     */
    private <T extends Message> T serializeAndDeserialize(T message) throws Exception {
        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(message);
        oos.close();
        
        byte[] serialized = baos.toByteArray();
        
        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
        ObjectInputStream ois = new ObjectInputStream(bais);
        @SuppressWarnings("unchecked")
        T deserialized = (T) ois.readObject();
        ois.close();
        
        return deserialized;
    }
    
    /**
     * Test class for complex value serialization.
     */
    private static class ComplexValue implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String data;
        private final int number;
        
        ComplexValue(String data, int number) {
            this.data = data;
            this.number = number;
        }
    }
}
