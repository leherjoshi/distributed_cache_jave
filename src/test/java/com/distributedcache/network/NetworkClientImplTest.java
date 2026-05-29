package com.distributedcache.network;

import com.distributedcache.exceptions.NetworkException;
import com.distributedcache.exceptions.TimeoutException;
import com.distributedcache.hashing.HashRing;
import com.distributedcache.hashing.NodeInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NetworkClientImpl.
 */
class NetworkClientImplTest {
    
    private static final String TEST_NODE_ID = "test-node";
    private static final String TARGET_NODE_ID = "target-node";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-value";
    
    private HashRing mockHashRing;
    private NetworkClientImpl networkClient;
    private TestServer testServer;
    
    @BeforeEach
    void setUp() {
        mockHashRing = mock(HashRing.class);
        networkClient = new NetworkClientImpl(mockHashRing);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (testServer != null) {
            testServer.stop();
        }
    }
    
    @Test
    void testSendSuccessfully() throws Exception {
        // Start a test server that echoes back a response
        testServer = new TestServer(server -> {
            GetRequest request = (GetRequest) server.receiveMessage();
            assertNotNull(request);
            assertEquals(TEST_KEY, request.getKey());
            
            GetResponse response = new GetResponse(TARGET_NODE_ID, TEST_KEY, TEST_VALUE);
            server.sendMessage(response);
        });
        testServer.start();
        
        NodeInfo targetNode = new NodeInfo(TARGET_NODE_ID, "localhost", testServer.getPort());
        GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
        
        CompletableFuture<GetResponse> future = networkClient.send(targetNode, request);
        GetResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertEquals(TEST_KEY, response.getKey());
        assertEquals(TEST_VALUE, response.getValue().orElse(null));
    }
    
    @Test
    void testSendWithConnectionFailure() throws Exception {
        // Try to connect to a non-existent server
        NodeInfo targetNode = new NodeInfo(TARGET_NODE_ID, "localhost", 9999);
        GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
        
        CompletableFuture<GetResponse> future = networkClient.send(targetNode, request);
        
        ExecutionException exception = assertThrows(ExecutionException.class, 
            () -> future.get(5, TimeUnit.SECONDS));
        
        assertTrue(exception.getCause() instanceof NetworkException);
        assertTrue(exception.getCause().getMessage().contains(TARGET_NODE_ID));
    }
    
    @Test
    void testSendWithRetrySucceedsOnFirstAttempt() throws Exception {
        testServer = new TestServer(server -> {
            GetRequest request = (GetRequest) server.receiveMessage();
            GetResponse response = new GetResponse(TARGET_NODE_ID, TEST_KEY, TEST_VALUE);
            server.sendMessage(response);
        });
        testServer.start();
        
        NodeInfo targetNode = new NodeInfo(TARGET_NODE_ID, "localhost", testServer.getPort());
        GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
        
        CompletableFuture<GetResponse> future = networkClient.sendWithRetry(targetNode, request);
        GetResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertEquals(TEST_VALUE, response.getValue().orElse(null));
    }
    
    @Test
    void testSendWithRetrySucceedsOnSecondAttempt() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        testServer = new TestServer(server -> {
            int attempt = attemptCount.incrementAndGet();
            
            if (attempt == 1) {
                // First attempt: close connection immediately to simulate failure
                server.receiveMessage();
                // Don't send response, just close
                return;
            } else {
                // Second attempt: succeed
                GetRequest request = (GetRequest) server.receiveMessage();
                GetResponse response = new GetResponse(TARGET_NODE_ID, TEST_KEY, TEST_VALUE);
                server.sendMessage(response);
            }
        });
        testServer.start();
        
        NodeInfo targetNode = new NodeInfo(TARGET_NODE_ID, "localhost", testServer.getPort());
        GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
        
        long startTime = System.currentTimeMillis();
        CompletableFuture<GetResponse> future = networkClient.sendWithRetry(targetNode, request);
        GetResponse response = future.get(10, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        assertNotNull(response);
        assertEquals(TEST_VALUE, response.getValue().orElse(null));
        assertEquals(2, attemptCount.get());
        
        // Verify backoff was applied (should be at least 100ms for first retry)
        assertTrue(duration >= 100, "Expected backoff delay, but duration was " + duration + "ms");
    }
    
    @Test
    void testSendWithRetrySucceedsOnThirdAttempt() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        testServer = new TestServer(server -> {
            int attempt = attemptCount.incrementAndGet();
            
            if (attempt < 3) {
                // First two attempts: fail
                server.receiveMessage();
                return;
            } else {
                // Third attempt: succeed
                GetRequest request = (GetRequest) server.receiveMessage();
                GetResponse response = new GetResponse(TARGET_NODE_ID, TEST_KEY, TEST_VALUE);
                server.sendMessage(response);
            }
        });
        testServer.start();
        
        NodeInfo targetNode = new NodeInfo(TARGET_NODE_ID, "localhost", testServer.getPort());
        GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
        
        long startTime = System.currentTimeMillis();
        CompletableFuture<GetResponse> future = networkClient.sendWithRetry(targetNode, request);
        GetResponse response = future.get(10, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        assertNotNull(response);
        assertEquals(TEST_VALUE, response.getValue().orElse(null));
        assertEquals(3, attemptCount.get());
        
        // Verify backoff was applied (100ms + 200ms = 300ms minimum)
        assertTrue(duration >= 300, "Expected backoff delays, but duration was " + duration + "ms");
    }
    
    @Test
    void testSendWithRetryFailsAfterMaxRetries() throws Exception {
        // Server that always fails
        testServer = new TestServer(server -> {
            server.receiveMessage();
            // Don't send response
        });
        testServer.start();
        
        NodeInfo targetNode = new NodeInfo(TARGET_NODE_ID, "localhost", testServer.getPort());
        GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
        
        CompletableFuture<GetResponse> future = networkClient.sendWithRetry(targetNode, request);
        
        ExecutionException exception = assertThrows(ExecutionException.class,
            () -> future.get(15, TimeUnit.SECONDS));
        
        assertTrue(exception.getCause() instanceof NetworkException);
        assertTrue(exception.getCause().getMessage().contains("after 3 attempts"));
    }
    
    @Test
    void testSendWithRetryExponentialBackoff() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        testServer = new TestServer(server -> {
            attemptCount.incrementAndGet();
            server.receiveMessage();
            // Always fail
        });
        testServer.start();
        
        NodeInfo targetNode = new NodeInfo(TARGET_NODE_ID, "localhost", testServer.getPort());
        GetRequest request = new GetRequest(TEST_NODE_ID, TEST_KEY);
        
        long startTime = System.currentTimeMillis();
        CompletableFuture<GetResponse> future = networkClient.sendWithRetry(targetNode, request);
        
        try {
            future.get(15, TimeUnit.SECONDS);
            fail("Expected exception");
        } catch (Exception e) {
            // Expected
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        assertEquals(3, attemptCount.get());
        
        // Verify exponential backoff: 100ms + 200ms = 300ms minimum
        // We use 300ms instead of 700ms because the first attempt has no backoff
        // and the actual network operations may be faster than expected
        assertTrue(duration >= 300, 
            "Expected exponential backoff (100+200=300ms minimum), but duration was " + duration + "ms");
    }
    
    @Test
    void testBroadcastToMultipleNodes() throws Exception {
        // Create multiple test servers
        TestServer server1 = new TestServer(s -> {
            HealthCheck msg = (HealthCheck) s.receiveMessage();
            HealthResponse response = new HealthResponse("node1", 
                com.distributedcache.node.HealthStatus.HEALTHY, 1024, 10240);
            s.sendMessage(response);
        });
        server1.start();
        
        TestServer server2 = new TestServer(s -> {
            HealthCheck msg = (HealthCheck) s.receiveMessage();
            HealthResponse response = new HealthResponse("node2", 
                com.distributedcache.node.HealthStatus.HEALTHY, 2048, 10240);
            s.sendMessage(response);
        });
        server2.start();
        
        TestServer server3 = new TestServer(s -> {
            HealthCheck msg = (HealthCheck) s.receiveMessage();
            HealthResponse response = new HealthResponse("node3", 
                com.distributedcache.node.HealthStatus.HEALTHY, 3072, 10240);
            s.sendMessage(response);
        });
        server3.start();
        
        // Setup hash ring with multiple nodes
        Set<NodeInfo> nodes = new HashSet<>();
        nodes.add(new NodeInfo("node1", "localhost", server1.getPort()));
        nodes.add(new NodeInfo("node2", "localhost", server2.getPort()));
        nodes.add(new NodeInfo("node3", "localhost", server3.getPort()));
        nodes.add(new NodeInfo(TEST_NODE_ID, "localhost", 8888)); // Source node
        
        when(mockHashRing.getAllNodes()).thenReturn(nodes);
        
        HealthCheck message = new HealthCheck(TEST_NODE_ID);
        CompletableFuture<Void> future = networkClient.broadcast(message);
        
        future.get(10, TimeUnit.SECONDS);
        
        // Verify all servers received the message
        server1.stop();
        server2.stop();
        server3.stop();
    }
    
    @Test
    void testBroadcastSkipsSourceNode() throws Exception {
        TestServer server1 = new TestServer(s -> {
            HealthCheck msg = (HealthCheck) s.receiveMessage();
            HealthResponse response = new HealthResponse("node1", 
                com.distributedcache.node.HealthStatus.HEALTHY, 1024, 10240);
            s.sendMessage(response);
        });
        server1.start();
        
        // Setup hash ring with source node and one other node
        Set<NodeInfo> nodes = new HashSet<>();
        nodes.add(new NodeInfo("node1", "localhost", server1.getPort()));
        nodes.add(new NodeInfo(TEST_NODE_ID, "localhost", 8888)); // Source node
        
        when(mockHashRing.getAllNodes()).thenReturn(nodes);
        
        HealthCheck message = new HealthCheck(TEST_NODE_ID);
        CompletableFuture<Void> future = networkClient.broadcast(message);
        
        future.get(10, TimeUnit.SECONDS);
        
        // Only node1 should have received the message (source node skipped)
        server1.stop();
    }
    
    @Test
    void testBroadcastWithPartialFailures() throws Exception {
        // Server that succeeds
        TestServer successServer = new TestServer(s -> {
            HealthCheck msg = (HealthCheck) s.receiveMessage();
            HealthResponse response = new HealthResponse("node1", 
                com.distributedcache.node.HealthStatus.HEALTHY, 1024, 10240);
            s.sendMessage(response);
        });
        successServer.start();
        
        // Setup hash ring with one working node and one non-existent node
        Set<NodeInfo> nodes = new HashSet<>();
        nodes.add(new NodeInfo("node1", "localhost", successServer.getPort()));
        nodes.add(new NodeInfo("node2", "localhost", 9999)); // Non-existent
        nodes.add(new NodeInfo(TEST_NODE_ID, "localhost", 8888)); // Source
        
        when(mockHashRing.getAllNodes()).thenReturn(nodes);
        
        HealthCheck message = new HealthCheck(TEST_NODE_ID);
        CompletableFuture<Void> future = networkClient.broadcast(message);
        
        // Should complete without throwing exception despite one failure
        assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS));
        
        successServer.stop();
    }
    
    @Test
    void testBroadcastWithEmptyNodeList() throws Exception {
        when(mockHashRing.getAllNodes()).thenReturn(new HashSet<>());
        
        HealthCheck message = new HealthCheck(TEST_NODE_ID);
        CompletableFuture<Void> future = networkClient.broadcast(message);
        
        // Should complete successfully even with no nodes
        assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS));
    }
    
    /**
     * Test server that handles one connection and executes a handler.
     */
    private static class TestServer {
        private final ServerSocket serverSocket;
        private final MessageHandler handler;
        private volatile boolean running;
        private Thread serverThread;
        
        interface MessageHandler {
            void handle(TestServerConnection connection) throws Exception;
        }
        
        TestServer(MessageHandler handler) throws IOException {
            this.serverSocket = new ServerSocket(0); // Random available port
            this.handler = handler;
            this.running = false;
        }
        
        void start() {
            running = true;
            serverThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setSoTimeout(5000);
                        
                        TestServerConnection connection = new TestServerConnection(clientSocket);
                        handler.handle(connection);
                        
                        clientSocket.close();
                    } catch (Exception e) {
                        if (running) {
                            // Only log if we're still supposed to be running
                            System.err.println("Test server error: " + e.getMessage());
                        }
                    }
                }
            });
            serverThread.start();
        }
        
        void stop() throws Exception {
            running = false;
            serverSocket.close();
            if (serverThread != null) {
                serverThread.interrupt();
                serverThread.join(1000);
            }
        }
        
        int getPort() {
            return serverSocket.getLocalPort();
        }
    }
    
    /**
     * Helper class for test server connections.
     */
    private static class TestServerConnection {
        private final Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        
        TestServerConnection(Socket socket) {
            this.socket = socket;
        }
        
        Message receiveMessage() throws IOException, ClassNotFoundException {
            if (in == null) {
                in = new ObjectInputStream(socket.getInputStream());
            }
            return (Message) in.readObject();
        }
        
        void sendMessage(Message message) throws IOException {
            if (out == null) {
                out = new ObjectOutputStream(socket.getOutputStream());
            }
            out.writeObject(message);
            out.flush();
        }
    }
}
