package com.distributedcache.utils;

import com.distributedcache.exceptions.SerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JavaMessageSerializer.
 */
class JavaMessageSerializerTest {
    
    private JavaMessageSerializer serializer;
    
    @BeforeEach
    void setUp() {
        serializer = new JavaMessageSerializer();
    }
    
    // ========== Serialize Tests ==========
    
    @Test
    @DisplayName("serialize() should convert a String to byte array")
    void testSerializeString() throws SerializationException {
        String input = "Hello, World!";
        
        byte[] result = serializer.serialize(input);
        
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    @DisplayName("serialize() should convert an Integer to byte array")
    void testSerializeInteger() throws SerializationException {
        Integer input = 42;
        
        byte[] result = serializer.serialize(input);
        
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    @DisplayName("serialize() should convert a List to byte array")
    void testSerializeList() throws SerializationException {
        List<String> input = Arrays.asList("one", "two", "three");
        
        byte[] result = serializer.serialize(input);
        
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    @DisplayName("serialize() should convert a Map to byte array")
    void testSerializeMap() throws SerializationException {
        Map<String, Integer> input = new HashMap<>();
        input.put("one", 1);
        input.put("two", 2);
        
        byte[] result = serializer.serialize(input);
        
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    @DisplayName("serialize() should convert a custom Serializable object to byte array")
    void testSerializeCustomObject() throws SerializationException {
        SerializableTestObject input = new SerializableTestObject("test", 123);
        
        byte[] result = serializer.serialize(input);
        
        assertNotNull(result);
        assertTrue(result.length > 0);
    }
    
    @Test
    @DisplayName("serialize() should throw SerializationException for null object")
    void testSerializeNullObject() {
        SerializationException exception = assertThrows(
            SerializationException.class,
            () -> serializer.serialize(null)
        );
        
        assertTrue(exception.getMessage().contains("null"));
    }
    
    @Test
    @DisplayName("serialize() should throw SerializationException for non-serializable object")
    void testSerializeNonSerializableObject() {
        NonSerializableTestObject input = new NonSerializableTestObject("test");
        
        SerializationException exception = assertThrows(
            SerializationException.class,
            () -> serializer.serialize(input)
        );
        
        assertTrue(exception.getMessage().contains("Serializable"));
    }
    
    @Test
    @DisplayName("serialize() should throw SerializationException for object with non-serializable fields")
    void testSerializeObjectWithNonSerializableField() {
        ObjectWithNonSerializableField input = new ObjectWithNonSerializableField();
        
        SerializationException exception = assertThrows(
            SerializationException.class,
            () -> serializer.serialize(input)
        );
        
        assertTrue(exception.getMessage().contains("non-serializable"));
    }
    
    // ========== Deserialize Tests ==========
    
    @Test
    @DisplayName("deserialize() should convert byte array back to String")
    void testDeserializeString() throws SerializationException {
        String original = "Hello, World!";
        byte[] serialized = serializer.serialize(original);
        
        String result = serializer.deserialize(serialized, String.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("deserialize() should convert byte array back to Integer")
    void testDeserializeInteger() throws SerializationException {
        Integer original = 42;
        byte[] serialized = serializer.serialize(original);
        
        Integer result = serializer.deserialize(serialized, Integer.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("deserialize() should convert byte array back to List")
    void testDeserializeList() throws SerializationException {
        List<String> original = Arrays.asList("one", "two", "three");
        byte[] serialized = serializer.serialize(original);
        
        @SuppressWarnings("unchecked")
        List<String> result = serializer.deserialize(serialized, List.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("deserialize() should convert byte array back to Map")
    void testDeserializeMap() throws SerializationException {
        Map<String, Integer> original = new HashMap<>();
        original.put("one", 1);
        original.put("two", 2);
        byte[] serialized = serializer.serialize(original);
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> result = serializer.deserialize(serialized, Map.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("deserialize() should convert byte array back to custom object")
    void testDeserializeCustomObject() throws SerializationException {
        SerializableTestObject original = new SerializableTestObject("test", 123);
        byte[] serialized = serializer.serialize(original);
        
        SerializableTestObject result = serializer.deserialize(serialized, SerializableTestObject.class);
        
        assertEquals(original.getName(), result.getName());
        assertEquals(original.getValue(), result.getValue());
    }
    
    @Test
    @DisplayName("deserialize() should throw SerializationException for null byte array")
    void testDeserializeNullBytes() {
        SerializationException exception = assertThrows(
            SerializationException.class,
            () -> serializer.deserialize(null, String.class)
        );
        
        assertTrue(exception.getMessage().contains("null"));
    }
    
    @Test
    @DisplayName("deserialize() should throw SerializationException for empty byte array")
    void testDeserializeEmptyBytes() {
        byte[] empty = new byte[0];
        
        SerializationException exception = assertThrows(
            SerializationException.class,
            () -> serializer.deserialize(empty, String.class)
        );
        
        assertTrue(exception.getMessage().contains("empty"));
    }
    
    @Test
    @DisplayName("deserialize() should throw SerializationException for null type")
    void testDeserializeNullType() throws SerializationException {
        String original = "test";
        byte[] serialized = serializer.serialize(original);
        
        SerializationException exception = assertThrows(
            SerializationException.class,
            () -> serializer.deserialize(serialized, null)
        );
        
        assertTrue(exception.getMessage().contains("type"));
    }
    
    @Test
    @DisplayName("deserialize() should throw SerializationException for type mismatch")
    void testDeserializeTypeMismatch() throws SerializationException {
        String original = "test";
        byte[] serialized = serializer.serialize(original);
        
        SerializationException exception = assertThrows(
            SerializationException.class,
            () -> serializer.deserialize(serialized, Integer.class)
        );
        
        assertTrue(exception.getMessage().contains("does not match"));
    }
    
    @Test
    @DisplayName("deserialize() should throw SerializationException for corrupted data")
    void testDeserializeCorruptedData() {
        byte[] corrupted = new byte[]{1, 2, 3, 4, 5};
        
        SerializationException exception = assertThrows(
            SerializationException.class,
            () -> serializer.deserialize(corrupted, String.class)
        );
        
        assertNotNull(exception.getMessage());
    }
    
    // ========== Round-Trip Tests ==========
    
    @Test
    @DisplayName("serialize() then deserialize() should return equivalent String")
    void testRoundTripString() throws SerializationException {
        String original = "Test String";
        
        byte[] serialized = serializer.serialize(original);
        String result = serializer.deserialize(serialized, String.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("serialize() then deserialize() should return equivalent Integer")
    void testRoundTripInteger() throws SerializationException {
        Integer original = 12345;
        
        byte[] serialized = serializer.serialize(original);
        Integer result = serializer.deserialize(serialized, Integer.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("serialize() then deserialize() should return equivalent Long")
    void testRoundTripLong() throws SerializationException {
        Long original = 9876543210L;
        
        byte[] serialized = serializer.serialize(original);
        Long result = serializer.deserialize(serialized, Long.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("serialize() then deserialize() should return equivalent Double")
    void testRoundTripDouble() throws SerializationException {
        Double original = 3.14159;
        
        byte[] serialized = serializer.serialize(original);
        Double result = serializer.deserialize(serialized, Double.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("serialize() then deserialize() should return equivalent Boolean")
    void testRoundTripBoolean() throws SerializationException {
        Boolean original = true;
        
        byte[] serialized = serializer.serialize(original);
        Boolean result = serializer.deserialize(serialized, Boolean.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("serialize() then deserialize() should return equivalent List")
    void testRoundTripList() throws SerializationException {
        List<String> original = Arrays.asList("alpha", "beta", "gamma");
        
        byte[] serialized = serializer.serialize(original);
        @SuppressWarnings("unchecked")
        List<String> result = serializer.deserialize(serialized, List.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("serialize() then deserialize() should return equivalent Map")
    void testRoundTripMap() throws SerializationException {
        Map<String, Integer> original = new HashMap<>();
        original.put("a", 1);
        original.put("b", 2);
        original.put("c", 3);
        
        byte[] serialized = serializer.serialize(original);
        @SuppressWarnings("unchecked")
        Map<String, Integer> result = serializer.deserialize(serialized, Map.class);
        
        assertEquals(original, result);
    }
    
    @Test
    @DisplayName("serialize() then deserialize() should return equivalent custom object")
    void testRoundTripCustomObject() throws SerializationException {
        SerializableTestObject original = new SerializableTestObject("custom", 999);
        
        byte[] serialized = serializer.serialize(original);
        SerializableTestObject result = serializer.deserialize(serialized, SerializableTestObject.class);
        
        assertEquals(original.getName(), result.getName());
        assertEquals(original.getValue(), result.getValue());
    }
    
    // ========== EstimateSize Tests ==========
    
    @Test
    @DisplayName("estimateSize() should return positive size for String")
    void testEstimateSizeString() {
        String input = "Hello, World!";
        
        long size = serializer.estimateSize(input);
        
        assertTrue(size > 0);
    }
    
    @Test
    @DisplayName("estimateSize() should return positive size for Integer")
    void testEstimateSizeInteger() {
        Integer input = 42;
        
        long size = serializer.estimateSize(input);
        
        assertTrue(size > 0);
    }
    
    @Test
    @DisplayName("estimateSize() should return positive size for custom object")
    void testEstimateSizeCustomObject() {
        SerializableTestObject input = new SerializableTestObject("test", 123);
        
        long size = serializer.estimateSize(input);
        
        assertTrue(size > 0);
    }
    
    @Test
    @DisplayName("estimateSize() should return 0 for null object")
    void testEstimateSizeNull() {
        long size = serializer.estimateSize(null);
        
        assertEquals(0, size);
    }
    
    @Test
    @DisplayName("estimateSize() should return 0 for non-serializable object")
    void testEstimateSizeNonSerializable() {
        NonSerializableTestObject input = new NonSerializableTestObject("test");
        
        long size = serializer.estimateSize(input);
        
        assertEquals(0, size);
    }
    
    @Test
    @DisplayName("estimateSize() should return larger size for larger objects")
    void testEstimateSizeComparison() {
        String small = "a";
        String large = "a".repeat(1000);
        
        long smallSize = serializer.estimateSize(small);
        long largeSize = serializer.estimateSize(large);
        
        assertTrue(largeSize > smallSize);
    }
    
    @Test
    @DisplayName("estimateSize() should match actual serialized size")
    void testEstimateSizeAccuracy() throws SerializationException {
        String input = "Test String for Size Estimation";
        
        long estimatedSize = serializer.estimateSize(input);
        byte[] serialized = serializer.serialize(input);
        
        assertEquals(serialized.length, estimatedSize);
    }
    
    // ========== Edge Cases ==========
    
    @Test
    @DisplayName("serialize() should handle empty String")
    void testSerializeEmptyString() throws SerializationException {
        String input = "";
        
        byte[] result = serializer.serialize(input);
        String deserialized = serializer.deserialize(result, String.class);
        
        assertEquals(input, deserialized);
    }
    
    @Test
    @DisplayName("serialize() should handle empty List")
    void testSerializeEmptyList() throws SerializationException {
        List<String> input = new ArrayList<>();
        
        byte[] result = serializer.serialize(input);
        @SuppressWarnings("unchecked")
        List<String> deserialized = serializer.deserialize(result, List.class);
        
        assertEquals(input, deserialized);
    }
    
    @Test
    @DisplayName("serialize() should handle empty Map")
    void testSerializeEmptyMap() throws SerializationException {
        Map<String, Integer> input = new HashMap<>();
        
        byte[] result = serializer.serialize(input);
        @SuppressWarnings("unchecked")
        Map<String, Integer> deserialized = serializer.deserialize(result, Map.class);
        
        assertEquals(input, deserialized);
    }
    
    @Test
    @DisplayName("serialize() should handle nested collections")
    void testSerializeNestedCollections() throws SerializationException {
        List<List<String>> input = Arrays.asList(
            Arrays.asList("a", "b"),
            Arrays.asList("c", "d")
        );
        
        byte[] result = serializer.serialize(input);
        @SuppressWarnings("unchecked")
        List<List<String>> deserialized = serializer.deserialize(result, List.class);
        
        assertEquals(input, deserialized);
    }
    
    // ========== Test Helper Classes ==========
    
    /**
     * Serializable test object for testing custom object serialization.
     */
    static class SerializableTestObject implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final String name;
        private final int value;
        
        public SerializableTestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() {
            return name;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * Non-serializable test object for testing error handling.
     */
    static class NonSerializableTestObject {
        private final String name;
        
        public NonSerializableTestObject(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
    }
    
    /**
     * Serializable object with a non-serializable field for testing error handling.
     */
    static class ObjectWithNonSerializableField implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final NonSerializableTestObject field = new NonSerializableTestObject("test");
        
        public NonSerializableTestObject getField() {
            return field;
        }
    }
}
