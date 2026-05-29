package com.distributedcache.network;

import com.distributedcache.exceptions.NetworkException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NetworkServerImpl.
 */
class NetworkServerImplTest {
    
    private static final String TEST_NODE_ID = "test-node";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-value";
    private static final int TEST_PORT = 9999;
    
    private NetworkServerImpl server;
    
    @BeforeEach
    void setUp() {
        server = new NetworkServerImpl(TEST_PORT);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }
    
    @Test
    void testConstructorWithValidPort() {
        NetworkServerImpl server = new NetworkServerImpl(8080);
        assertEquals(8080, server.getPort());
    }
    
    @Test
    void testConstructorWithInvalidPortTooLow() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkServerImpl(0));
    }
    
    @Test
    void testConstructorWithInvalidPortTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> new NetworkServerImpl(65536));
    }
    
    @Test
    void testStartServerSuccessfully() throws NetworkException {
        server.start();
        
        // Verify server is running by checking if we can connect
        assertDoesNotThrow(() -> {
            try (SocketChannel client = SocketChannel.open()) {
                client.connect(new InetSocketAddress("localhost", TEST_PORT));
                assertTrue(client.isConnected());
            }
        });
    }
    
    @Test
    void testStartServerTwiceThrowsException() throws NetworkException {
        server.start();
        assertThrows(NetworkException.class, () -> server.start());
    }
    
    @Test
    void testStopServerSuccessfully() throws NetworkException {
        server.start();
        server.stop();
        
        // Verify server is stopped by checking connection fails
        assertThrows(IOException.class, () -> {
            try (SocketChannel client = SocketChannel.open()) {
                client.socket().connect(new InetSocketAddress("localhost", TEST_PORT), 1000);
            }
        });
    }
    
    @Test
    void testStopServerWhenNotRunning() {
        assertDoesNotThrow(() -> server.stop());
    }
    
    @Test
    void testRegisterHandlerSuccessfully() {
        MessageHandler handler = message -> null;
        assertDoesNotThrow(() -> server.registerHandler(MessageType.GET_REQUEST, handler));
    }
    
    @Test
    void testRegisterHandlerWithNullType() {
        MessageHandler handler = message -> null;
        assertThrows(IllegalArgumentException.class, 
            () -> server.registerHandler(null, handler));
    }
    
    @Test
    void testRegisterHandlerWithNullHandler() {
        assertThrows(IllegalArgumentException.class, 
            () -> server.registerHandler(MessageType.GET_REQUEST, null));
    }
    
    @Test
    void testGetPort() {
        assertEquals(TEST_PORT, server.getPort());
    }
    
    @Test
    void testHandleGetRequest() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();
        
        // Register handler
        server.registerHandler(MessageType.GET_REQUEST, message -> {
            receivedMessage.set(message);
            latch.countDown();
            return new GetResponse(TEST_NODE_ID, ((GetRequest) message).getKey(), TEST_VALUE);
        });
        
        server.start();
        
        // Send request
        GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
        GetResponse response = sendMessageAndReceiveResponse(request, GetResponse.class);
        
        // Wait for handler to be called
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify handler received the message
        assertNotNull(receivedMessage.get());
        assertTrue(receivedMessage.get() instanceof GetRequest);
        assertEquals(TEST_KEY, ((GetRequest) receivedMessage.get()).getKey());
        
        // Verify response
        assertNotNull(response);
        assertEquals(TEST_KEY, response.getKey());
        assertTrue(response.isFound());
        assertEquals(TEST_VALUE, response.getValue().orElse(null));
    }
    
    @Test
    void testHandlePutRequest() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();
        
        // Register handler
        server.registerHandler(MessageType.PUT_REQUEST, message -> {
            receivedMessage.set(message);
            latch.countDown();
            return new PutResponse(TEST_NODE_ID, ((PutRequest) message).getKey(), true);
        });
        
        server.start();
        
        // Send request
        PutRequest request = new PutRequest(TEST_NODE_ID, TEST_KEY, TEST_VALUE);
        PutResponse response = sendMessageAndReceiveResponse(request, PutResponse.class);
        
        // Wait for handler to be called
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify handler received the message
        assertNotNull(receivedMessage.get());
        assertTrue(receivedMessage.get() instanceof PutRequest);
        assertEquals(TEST_KEY, ((PutRequest) receivedMessage.get()).getKey());
        assertEquals(TEST_VALUE, ((PutRequest) receivedMessage.get()).getValue());
        
        // Verify response
        assertNotNull(response);
        assertEquals(TEST_KEY, response.getKey());
        assertTrue(response.isSuccess());
    }
    
    @Test
    void testHandleDeleteRequest() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();
        
        // Register handler
        server.registerHandler(MessageType.DELETE_REQUEST, message -> {
            receivedMessage.set(message);
            latch.countDown();
            return new DeleteResponse(TEST_NODE_ID, ((DeleteRequest) message).getKey(), true);
        });
        
        server.start();
        
        // Send request
        DeleteRequest request = new DeleteRequest(TEST_NODE_ID, TEST_KEY);
        DeleteResponse response = sendMessageAndReceiveResponse(request, DeleteResponse.class);
        
        // Wait for handler to be called
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify handler received the message
        assertNotNull(receivedMessage.get());
        assertTrue(receivedMessage.get() instanceof DeleteRequest);
        assertEquals(TEST_KEY, ((DeleteRequest) receivedMessage.get()).getKey());
        
        // Verify response
        assertNotNull(response);
        assertEquals(TEST_KEY, response.getKey());
        assertTrue(response.isSuccess());
    }
    
    @Test
    void testHandleHealthCheck() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        // Register handler
        server.registerHandler(MessageType.HEALTH_CHECK, message -> {
            latch.countDown();
            return new HealthResponse(TEST_NODE_ID, 
                com.distributedcache.node.HealthStatus.HEALTHY, 
                1024 * 1024, 
                10 * 1024 * 1024);
        });
        
        server.start();
        
        // Send request
        HealthCheck request = new HealthCheck(TEST_NODE_ID);
        HealthResponse response = sendMessageAndReceiveResponse(request, HealthResponse.class);
        
        // Wait for handler to be called
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Verify response
        assertNotNull(response);
        assertEquals(com.distributedcache.node.HealthStatus.HEALTHY, response.getStatus());
    }
    
    @Test
    void testConcurrentConnections() throws Exception {
        AtomicInteger handlerCallCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);
        
        // Register handler
        server.registerHandler(MessageType.GET_REQUEST, message -> {
            handlerCallCount.incrementAndGet();
            latch.countDown();
            return new GetResponse(TEST_NODE_ID, ((GetRequest) message).getKey(), TEST_VALUE);
        });
        
        server.start();
        
        // Send multiple concurrent requests
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                try {
                    GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY + index);
                    GetResponse response = sendMessageAndReceiveResponse(request, GetResponse.class);
                    assertNotNull(response);
                } catch (Exception e) {
                    fail("Failed to send request: " + e.getMessage());
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000);
        }
        
        // Wait for all handlers to be called
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertEquals(10, handlerCallCount.get());
    }
    
    @Test
    void testHandlerReturnsNull() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        
        // Register handler that returns null (no response)
        server.registerHandler(MessageType.GET_REQUEST, message -> {
            latch.countDown();
            return null;
        });
        
        server.start();
        
        // Send request - connection should close after processing
        try (SocketChannel client = SocketChannel.open()) {
            client.connect(new InetSocketAddress("localhost", TEST_PORT));
            
            GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
            byte[] requestBytes = serializeMessage(request);
            
            ByteBuffer buffer = ByteBuffer.allocate(4 + requestBytes.length);
            buffer.putInt(requestBytes.length);
            buffer.put(requestBytes);
            buffer.flip();
            
            client.write(buffer);
            
            // Wait for handler to be called
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            
            // Try to read response - should get -1 (connection closed)
            ByteBuffer responseBuffer = ByteBuffer.allocate(4);
            int bytesRead = client.read(responseBuffer);
            
            // Connection should be closed (bytesRead == -1) or no data available (bytesRead == 0)
            assertTrue(bytesRead <= 0);
        }
    }
    
    @Test
    void testMultipleHandlerRegistrations() throws Exception {
        CountDownLatch getLatch = new CountDownLatch(1);
        CountDownLatch putLatch = new CountDownLatch(1);
        
        // Register multiple handlers
        server.registerHandler(MessageType.GET_REQUEST, message -> {
            getLatch.countDown();
            return new GetResponse(TEST_NODE_ID, ((GetRequest) message).getKey(), TEST_VALUE);
        });
        
        server.registerHandler(MessageType.PUT_REQUEST, message -> {
            putLatch.countDown();
            return new PutResponse(TEST_NODE_ID, ((PutRequest) message).getKey(), true);
        });
        
        server.start();
        
        // Send GET request
        GetRequest getRequest = new GetRequest(TEST_NODE_ID, TEST_KEY);
        GetResponse getResponse = sendMessageAndReceiveResponse(getRequest, GetResponse.class);
        assertNotNull(getResponse);
        assertTrue(getLatch.await(5, TimeUnit.SECONDS));
        
        // Send PUT request
        PutRequest putRequest = new PutRequest(TEST_NODE_ID, TEST_KEY, TEST_VALUE);
        PutResponse putResponse = sendMessageAndReceiveResponse(putRequest, PutResponse.class);
        assertNotNull(putResponse);
        assertTrue(putLatch.await(5, TimeUnit.SECONDS));
    }
    
    @Test
    void testCloseServerWithAutoCloseable() throws Exception {
        server.start();
        
        // Use try-with-resources
        try (NetworkServerImpl autoCloseServer = server) {
            assertTrue(autoCloseServer.getPort() > 0);
        }
        
        // Verify server is stopped
        assertThrows(IOException.class, () -> {
            try (SocketChannel client = SocketChannel.open()) {
                client.socket().connect(new InetSocketAddress("localhost", TEST_PORT), 1000);
            }
        });
    }
    
    /**
     * Helper method to send a message and receive a response.
     */
    private <T extends Message> T sendMessageAndReceiveResponse(Message request, Class<T> responseType) 
            throws Exception {
        try (SocketChannel client = SocketChannel.open()) {
            client.connect(new InetSocketAddress("localhost", TEST_PORT));
            
            // Serialize and send request
            byte[] requestBytes = serializeMessage(request);
            
            ByteBuffer buffer = ByteBuffer.allocate(4 + requestBytes.length);
            buffer.putInt(requestBytes.length);
            buffer.put(requestBytes);
            buffer.flip();
            
            while (buffer.hasRemaining()) {
                client.write(buffer);
            }
            
            // Read response length
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            int totalRead = 0;
            while (totalRead < 4) {
                int bytesRead = client.read(lengthBuffer);
                if (bytesRead == -1) {
                    throw new IOException("Connection closed before receiving response length");
                }
                totalRead += bytesRead;
            }
            
            lengthBuffer.flip();
            int responseLength = lengthBuffer.getInt();
            
            // Read response data
            ByteBuffer responseBuffer = ByteBuffer.allocate(responseLength);
            totalRead = 0;
            while (totalRead < responseLength) {
                int bytesRead = client.read(responseBuffer);
                if (bytesRead == -1) {
                    throw new IOException("Connection closed before receiving full response");
                }
                totalRead += bytesRead;
            }
            
            responseBuffer.flip();
            byte[] responseBytes = new byte[responseBuffer.remaining()];
            responseBuffer.get(responseBytes);
            
            // Deserialize response
            return deserializeMessage(responseBytes, responseType);
        }
    }
    
    /**
     * Helper method to serialize a message.
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
     * Helper method to deserialize a message.
     */
    private <T extends Message> T deserializeMessage(byte[] bytes, Class<T> type) throws Exception {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
             java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bais)) {
            return type.cast(ois.readObject());
        }
    }
}
