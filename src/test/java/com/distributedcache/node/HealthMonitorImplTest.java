package com.distributedcache.node;

import com.distributedcache.hashing.NodeInfo;
import com.distributedcache.network.HealthCheck;
import com.distributedcache.network.HealthResponse;
import com.distributedcache.network.Message;
import com.distributedcache.network.NetworkClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HealthMonitorImpl.
 */
class HealthMonitorImplTest {
    
    private static final String SOURCE_NODE_ID = "source-node";
    private static final String NODE_1_ID = "node-1";
    private static final String NODE_2_ID = "node-2";
    private static final String NODE_3_ID = "node-3";
    
    private NetworkClient mockNetworkClient;
    private Set<NodeInfo> nodesToMonitor;
    private NodeInfo node1;
    private NodeInfo node2;
    private NodeInfo node3;
    private HealthMonitorImpl healthMonitor;
    
    @BeforeEach
    void setUp() {
        mockNetworkClient = mock(NetworkClient.class);
        
        node1 = new NodeInfo(NODE_1_ID, "localhost", 8001);
        node2 = new NodeInfo(NODE_2_ID, "localhost", 8002);
        node3 = new NodeInfo(NODE_3_ID, "localhost", 8003);
        
        nodesToMonitor = new HashSet<>();
        nodesToMonitor.add(node1);
        nodesToMonitor.add(node2);
        nodesToMonitor.add(node3);
    }
    
    @AfterEach
    void tearDown() {
        if (healthMonitor != null) {
            healthMonitor.stop();
        }
    }
    
    @Test
    void testConstructorInitializesNodesAsHealthy() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_1_ID));
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_2_ID));
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_3_ID));
    }
    
    @Test
    void testConstructorWithNullNetworkClientThrowsException() {
        assertThrows(NullPointerException.class, () -> 
            new HealthMonitorImpl(null, SOURCE_NODE_ID, nodesToMonitor));
    }
    
    @Test
    void testConstructorWithNullSourceNodeIdThrowsException() {
        assertThrows(NullPointerException.class, () -> 
            new HealthMonitorImpl(mockNetworkClient, null, nodesToMonitor));
    }
    
    @Test
    void testConstructorWithNullCheckIntervalThrowsException() {
        assertThrows(NullPointerException.class, () -> 
            new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor, null));
    }
    
    @Test
    void testStartSchedulesHealthChecks() throws Exception {
        // Setup mock to return successful responses
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_1_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        doReturn(successFuture).when(mockNetworkClient).send(any(NodeInfo.class), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            nodesToMonitor, Duration.ofMillis(100));
        
        healthMonitor.start();
        
        // Wait for at least one health check cycle
        Thread.sleep(200);
        
        // Verify health checks were sent
        verify(mockNetworkClient, atLeastOnce()).send(any(NodeInfo.class), any(HealthCheck.class));
    }
    
    @Test
    void testStopCancelsScheduledHealthChecks() throws Exception {
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_1_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        doReturn(successFuture).when(mockNetworkClient).send(any(NodeInfo.class), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            nodesToMonitor, Duration.ofMillis(100));
        
        healthMonitor.start();
        Thread.sleep(150);
        
        int callCountBeforeStop = org.mockito.Mockito.mockingDetails(mockNetworkClient)
            .getInvocations().size();
        
        healthMonitor.stop();
        Thread.sleep(200);
        
        int callCountAfterStop = org.mockito.Mockito.mockingDetails(mockNetworkClient)
            .getInvocations().size();
        
        // No new calls should be made after stop
        assertEquals(callCountBeforeStop, callCountAfterStop);
    }
    
    @Test
    void testGetNodeStatusReturnsCorrectStatus() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_1_ID));
    }
    
    @Test
    void testGetNodeStatusForUnknownNodeReturnsUnavailable() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        assertEquals(HealthStatus.UNAVAILABLE, healthMonitor.getNodeStatus("unknown-node"));
    }
    
    @Test
    void testGetNodeStatusWithNullNodeIdThrowsException() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        assertThrows(NullPointerException.class, () -> healthMonitor.getNodeStatus(null));
    }
    
    @Test
    void testGetHealthyNodesReturnsOnlyHealthyNodes() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        Set<NodeInfo> healthyNodes = healthMonitor.getHealthyNodes();
        
        assertEquals(3, healthyNodes.size());
        assertTrue(healthyNodes.contains(node1));
        assertTrue(healthyNodes.contains(node2));
        assertTrue(healthyNodes.contains(node3));
    }
    
    @Test
    void testNodeMarkedUnavailableAfterThreeConsecutiveFailures() throws Exception {
        // Setup mock to return failed futures
        CompletableFuture<HealthResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Connection failed"));
        
        doReturn(failedFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        // Manually trigger 3 health checks
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_1_ID));
        assertEquals(1, healthMonitor.getConsecutiveFailures(NODE_1_ID));
        
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_1_ID));
        assertEquals(2, healthMonitor.getConsecutiveFailures(NODE_1_ID));
        
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        assertEquals(HealthStatus.UNAVAILABLE, healthMonitor.getNodeStatus(NODE_1_ID));
        assertEquals(3, healthMonitor.getConsecutiveFailures(NODE_1_ID));
    }
    
    @Test
    void testNodeMarkedHealthyWhenRespondsAfterBeingUnavailable() throws Exception {
        // First 3 checks fail
        CompletableFuture<HealthResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Connection failed"));
        
        doReturn(failedFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        // Trigger 3 failures
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        
        assertEquals(HealthStatus.UNAVAILABLE, healthMonitor.getNodeStatus(NODE_1_ID));
        
        // Now make it succeed
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_1_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        doReturn(successFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_1_ID));
        assertEquals(0, healthMonitor.getConsecutiveFailures(NODE_1_ID));
    }
    
    @Test
    void testOnStatusChangeCallbackInvokedOnStatusChange() throws Exception {
        CompletableFuture<HealthResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Connection failed"));
        
        doReturn(failedFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger(0);
        List<NodeStatusEvent> events = new CopyOnWriteArrayList<>();
        
        healthMonitor.onStatusChange(event -> {
            callbackCount.incrementAndGet();
            events.add(event);
            latch.countDown();
        });
        
        // Trigger 3 failures to change status
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        
        // Wait for callback
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        
        assertEquals(1, callbackCount.get());
        assertEquals(1, events.size());
        
        NodeStatusEvent event = events.get(0);
        assertEquals(NODE_1_ID, event.getNode().getNodeId());
        assertEquals(HealthStatus.HEALTHY, event.getOldStatus());
        assertEquals(HealthStatus.UNAVAILABLE, event.getNewStatus());
        assertNotNull(event.getTimestamp());
    }
    
    @Test
    void testOnStatusChangeWithNullCallbackThrowsException() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        assertThrows(NullPointerException.class, () -> healthMonitor.onStatusChange(null));
    }
    
    @Test
    void testMultipleStatusChangeCallbacksInvoked() throws Exception {
        CompletableFuture<HealthResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Connection failed"));
        
        doReturn(failedFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        
        healthMonitor.onStatusChange(event -> latch1.countDown());
        healthMonitor.onStatusChange(event -> latch2.countDown());
        
        // Trigger 3 failures
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        
        assertTrue(latch1.await(2, TimeUnit.SECONDS));
        assertTrue(latch2.await(2, TimeUnit.SECONDS));
    }
    
    @Test
    void testCheckNodeWithNullNodeIdThrowsException() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        assertThrows(NullPointerException.class, () -> healthMonitor.checkNode(null));
    }
    
    @Test
    void testCheckNodeWithUnknownNodeIdLogsWarning() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        // Should not throw exception
        assertDoesNotThrow(() -> healthMonitor.checkNode("unknown-node"));
        
        // Should not send any health check
        verify(mockNetworkClient, never()).send(any(NodeInfo.class), any(HealthCheck.class));
    }
    
    @Test
    void testCheckNodeSendsHealthCheckMessage() throws Exception {
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_1_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        doReturn(successFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        
        ArgumentCaptor<HealthCheck> messageCaptor = ArgumentCaptor.forClass(HealthCheck.class);
        verify(mockNetworkClient).send(eq(node1), messageCaptor.capture());
        
        HealthCheck sentMessage = messageCaptor.getValue();
        assertEquals(SOURCE_NODE_ID, sentMessage.getSourceNodeId());
    }
    
    @Test
    void testAddNodeAddsToMonitoringList() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            new HashSet<>(), Duration.ofMillis(100));
        
        healthMonitor.addNode(node1);
        
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_1_ID));
        assertEquals(0, healthMonitor.getConsecutiveFailures(NODE_1_ID));
    }
    
    @Test
    void testAddNodeWithNullThrowsException() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            new HashSet<>(), Duration.ofMillis(100));
        
        assertThrows(NullPointerException.class, () -> healthMonitor.addNode(null));
    }
    
    @Test
    void testRemoveNodeRemovesFromMonitoringList() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            nodesToMonitor, Duration.ofMillis(100));
        
        healthMonitor.removeNode(NODE_1_ID);
        
        assertEquals(HealthStatus.UNAVAILABLE, healthMonitor.getNodeStatus(NODE_1_ID));
        assertNull(healthMonitor.getLastCheckTime(NODE_1_ID));
    }
    
    @Test
    void testRemoveNodeWithNullThrowsException() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            nodesToMonitor, Duration.ofMillis(100));
        
        assertThrows(NullPointerException.class, () -> healthMonitor.removeNode(null));
    }
    
    @Test
    void testLastCheckTimeUpdatedAfterHealthCheck() throws Exception {
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_1_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        doReturn(successFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        Instant before = Instant.now();
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        Instant after = Instant.now();
        
        Instant lastCheck = healthMonitor.getLastCheckTime(NODE_1_ID);
        assertNotNull(lastCheck);
        assertTrue(lastCheck.isAfter(before) || lastCheck.equals(before));
        assertTrue(lastCheck.isBefore(after) || lastCheck.equals(after));
    }
    
    @Test
    void testConsecutiveFailuresResetOnSuccess() throws Exception {
        // First 2 checks fail
        CompletableFuture<HealthResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Connection failed"));
        
        doReturn(failedFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        
        assertEquals(2, healthMonitor.getConsecutiveFailures(NODE_1_ID));
        
        // Third check succeeds
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_1_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        doReturn(successFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        
        assertEquals(0, healthMonitor.getConsecutiveFailures(NODE_1_ID));
        assertEquals(HealthStatus.HEALTHY, healthMonitor.getNodeStatus(NODE_1_ID));
    }
    
    @Test
    void testHealthCheckTimeout() throws Exception {
        // Setup mock to return a future that never completes (simulating timeout)
        CompletableFuture<HealthResponse> timeoutFuture = new CompletableFuture<>();
        
        doReturn(timeoutFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(3000); // Wait for timeout (2 seconds + buffer)
        
        // Should count as a failure
        assertEquals(1, healthMonitor.getConsecutiveFailures(NODE_1_ID));
    }
    
    @Test
    void testPeriodicHealthChecksExecuteAtInterval() throws Exception {
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_1_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        doReturn(successFuture).when(mockNetworkClient).send(any(NodeInfo.class), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(200));
        
        healthMonitor.start();
        
        // Wait for multiple cycles
        Thread.sleep(650);
        
        // Should have executed at least 3 times (0ms, 200ms, 400ms, 600ms)
        verify(mockNetworkClient, atLeast(3)).send(eq(node1), any(HealthCheck.class));
    }
    
    @Test
    void testStartWhenAlreadyRunningLogsWarning() throws Exception {
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_1_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        doReturn(successFuture).when(mockNetworkClient).send(any(NodeInfo.class), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            nodesToMonitor, Duration.ofMillis(100));
        
        healthMonitor.start();
        
        // Starting again should not throw exception
        assertDoesNotThrow(() -> healthMonitor.start());
    }
    
    @Test
    void testStopWhenNotRunningLogsWarning() {
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, nodesToMonitor);
        
        // Stopping when not running should not throw exception
        assertDoesNotThrow(() -> healthMonitor.stop());
    }
    
    @Test
    void testGetHealthyNodesExcludesUnavailableNodes() throws Exception {
        CompletableFuture<HealthResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Connection failed"));
        
        CompletableFuture<HealthResponse> successFuture = CompletableFuture.completedFuture(
            new HealthResponse(NODE_2_ID, HealthStatus.HEALTHY, 1024, 10240)
        );
        
        doReturn(failedFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        doReturn(successFuture).when(mockNetworkClient).send(eq(node2), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1, node2), Duration.ofMillis(100));
        
        // Make node1 unavailable
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        
        Set<NodeInfo> healthyNodes = healthMonitor.getHealthyNodes();
        
        assertEquals(1, healthyNodes.size());
        assertTrue(healthyNodes.contains(node2));
        assertFalse(healthyNodes.contains(node1));
    }
    
    @Test
    void testStatusChangeEventContainsCorrectTimestamp() throws Exception {
        CompletableFuture<HealthResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Connection failed"));
        
        doReturn(failedFuture).when(mockNetworkClient).send(eq(node1), any(HealthCheck.class));
        
        healthMonitor = new HealthMonitorImpl(mockNetworkClient, SOURCE_NODE_ID, 
            Set.of(node1), Duration.ofMillis(100));
        
        CountDownLatch latch = new CountDownLatch(1);
        List<NodeStatusEvent> events = new CopyOnWriteArrayList<>();
        
        healthMonitor.onStatusChange(event -> {
            events.add(event);
            latch.countDown();
        });
        
        Instant before = Instant.now();
        
        // Trigger 3 failures
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        Thread.sleep(50);
        healthMonitor.checkNode(NODE_1_ID);
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        
        Instant after = Instant.now();
        
        NodeStatusEvent event = events.get(0);
        Instant eventTime = event.getTimestamp();
        
        assertTrue(eventTime.isAfter(before) || eventTime.equals(before));
        assertTrue(eventTime.isBefore(after) || eventTime.equals(after));
    }
}
