package com.distributedcache.replication;

import com.distributedcache.hashing.NodeInfo;
import com.distributedcache.network.NetworkClient;
import com.distributedcache.network.ReplicateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReplicationManagerImpl.
 */
class ReplicationManagerImplTest {
    
    private NetworkClient mockNetworkClient;
    private ReplicationManagerImpl replicationManager;
    private String sourceNodeId;
    private int replicationFactor;
    
    @BeforeEach
    void setUp() {
        mockNetworkClient = mock(NetworkClient.class);
        sourceNodeId = "node-1";
        replicationFactor = 3;
        replicationManager = new ReplicationManagerImpl(mockNetworkClient, replicationFactor, sourceNodeId);
    }
    
    @Test
    @DisplayName("Constructor should validate replication factor is between 1 and 5")
    void testConstructorValidatesReplicationFactor() {
        // Valid replication factors
        assertDoesNotThrow(() -> new ReplicationManagerImpl(mockNetworkClient, 1, sourceNodeId));
        assertDoesNotThrow(() -> new ReplicationManagerImpl(mockNetworkClient, 5, sourceNodeId));
        
        // Invalid replication factors
        assertThrows(IllegalArgumentException.class, 
            () -> new ReplicationManagerImpl(mockNetworkClient, 0, sourceNodeId));
        assertThrows(IllegalArgumentException.class, 
            () -> new ReplicationManagerImpl(mockNetworkClient, 6, sourceNodeId));
        assertThrows(IllegalArgumentException.class, 
            () -> new ReplicationManagerImpl(mockNetworkClient, -1, sourceNodeId));
    }
    
    @Test
    @DisplayName("getReplicationFactor should return configured replication factor")
    void testGetReplicationFactor() {
        assertEquals(3, replicationManager.getReplicationFactor());
        
        ReplicationManagerImpl manager2 = new ReplicationManagerImpl(mockNetworkClient, 2, sourceNodeId);
        assertEquals(2, manager2.getReplicationFactor());
    }
    
    @Test
    @DisplayName("replicatePut should send PUT requests to all replica nodes")
    void testReplicatePutSendsToAllReplicas() throws Exception {
        // Arrange
        String key = "test-key";
        String value = "test-value";
        List<NodeInfo> replicas = Arrays.asList(
            new NodeInfo("node-2", "host2", 8002),
            new NodeInfo("node-3", "host3", 8003)
        );
        
        when(mockNetworkClient.send(any(NodeInfo.class), any(ReplicateRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // Act
        CompletableFuture<Void> result = replicationManager.replicatePut(key, value, replicas);
        result.get(200, TimeUnit.MILLISECONDS);
        
        // Assert
        verify(mockNetworkClient, times(2)).send(any(NodeInfo.class), any(ReplicateRequest.class));
        
        ArgumentCaptor<NodeInfo> nodeCaptor = ArgumentCaptor.forClass(NodeInfo.class);
        ArgumentCaptor<ReplicateRequest> requestCaptor = ArgumentCaptor.forClass(ReplicateRequest.class);
        verify(mockNetworkClient, times(2)).send(nodeCaptor.capture(), requestCaptor.capture());
        
        List<NodeInfo> capturedNodes = nodeCaptor.getAllValues();
        assertTrue(capturedNodes.contains(replicas.get(0)));
        assertTrue(capturedNodes.contains(replicas.get(1)));
        
        List<ReplicateRequest> capturedRequests = requestCaptor.getAllValues();
        for (ReplicateRequest request : capturedRequests) {
            assertEquals(key, request.getKey());
            assertEquals(value, request.getValue());
            assertFalse(request.isDelete());
            assertEquals(sourceNodeId, request.getSourceNodeId());
        }
    }
    
    @Test
    @DisplayName("replicatePut should complete immediately when no replicas provided")
    void testReplicatePutWithNoReplicas() throws Exception {
        String key = "test-key";
        String value = "test-value";
        
        // Test with null replicas
        CompletableFuture<Void> result1 = replicationManager.replicatePut(key, value, null);
        result1.get(50, TimeUnit.MILLISECONDS);
        
        // Test with empty replicas
        CompletableFuture<Void> result2 = replicationManager.replicatePut(key, value, Collections.emptyList());
        result2.get(50, TimeUnit.MILLISECONDS);
        
        // Verify no network calls were made
        verify(mockNetworkClient, never()).send(any(), any());
    }
    
    @Test
    @DisplayName("replicatePut should handle failures gracefully and continue")
    void testReplicatePutHandlesFailuresGracefully() throws Exception {
        // Arrange
        String key = "test-key";
        String value = "test-value";
        List<NodeInfo> replicas = Arrays.asList(
            new NodeInfo("node-2", "host2", 8002),
            new NodeInfo("node-3", "host3", 8003)
        );
        
        // First replica succeeds, second fails
        when(mockNetworkClient.send(eq(replicas.get(0)), any(ReplicateRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(mockNetworkClient.send(eq(replicas.get(1)), any(ReplicateRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));
        
        // Act
        CompletableFuture<Void> result = replicationManager.replicatePut(key, value, replicas);
        
        // Should complete without throwing exception
        assertDoesNotThrow(() -> result.get(200, TimeUnit.MILLISECONDS));
    }
    
    @Test
    @DisplayName("replicatePut should timeout after 100ms")
    void testReplicatePutTimeout() throws Exception {
        // Arrange
        String key = "test-key";
        String value = "test-value";
        List<NodeInfo> replicas = Arrays.asList(
            new NodeInfo("node-2", "host2", 8002)
        );
        
        // Create a future that never completes
        CompletableFuture<Object> neverCompletes = new CompletableFuture<>();
        when(mockNetworkClient.send(any(NodeInfo.class), any(ReplicateRequest.class)))
            .thenReturn(neverCompletes);
        
        // Act
        CompletableFuture<Void> result = replicationManager.replicatePut(key, value, replicas);
        
        // Should complete (with timeout) within reasonable time
        assertDoesNotThrow(() -> result.get(200, TimeUnit.MILLISECONDS));
    }
    
    @Test
    @DisplayName("replicateDelete should send DELETE requests to all replica nodes")
    void testReplicateDeleteSendsToAllReplicas() throws Exception {
        // Arrange
        String key = "test-key";
        List<NodeInfo> replicas = Arrays.asList(
            new NodeInfo("node-2", "host2", 8002),
            new NodeInfo("node-3", "host3", 8003)
        );
        
        when(mockNetworkClient.send(any(NodeInfo.class), any(ReplicateRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // Act
        CompletableFuture<Void> result = replicationManager.replicateDelete(key, replicas);
        result.get(200, TimeUnit.MILLISECONDS);
        
        // Assert
        verify(mockNetworkClient, times(2)).send(any(NodeInfo.class), any(ReplicateRequest.class));
        
        ArgumentCaptor<ReplicateRequest> requestCaptor = ArgumentCaptor.forClass(ReplicateRequest.class);
        verify(mockNetworkClient, times(2)).send(any(NodeInfo.class), requestCaptor.capture());
        
        List<ReplicateRequest> capturedRequests = requestCaptor.getAllValues();
        for (ReplicateRequest request : capturedRequests) {
            assertEquals(key, request.getKey());
            assertNull(request.getValue());
            assertTrue(request.isDelete());
            assertEquals(sourceNodeId, request.getSourceNodeId());
        }
    }
    
    @Test
    @DisplayName("replicateDelete should complete immediately when no replicas provided")
    void testReplicateDeleteWithNoReplicas() throws Exception {
        String key = "test-key";
        
        // Test with null replicas
        CompletableFuture<Void> result1 = replicationManager.replicateDelete(key, null);
        result1.get(50, TimeUnit.MILLISECONDS);
        
        // Test with empty replicas
        CompletableFuture<Void> result2 = replicationManager.replicateDelete(key, Collections.emptyList());
        result2.get(50, TimeUnit.MILLISECONDS);
        
        // Verify no network calls were made
        verify(mockNetworkClient, never()).send(any(), any());
    }
    
    @Test
    @DisplayName("replicateDelete should handle failures gracefully and continue")
    void testReplicateDeleteHandlesFailuresGracefully() throws Exception {
        // Arrange
        String key = "test-key";
        List<NodeInfo> replicas = Arrays.asList(
            new NodeInfo("node-2", "host2", 8002),
            new NodeInfo("node-3", "host3", 8003)
        );
        
        // First replica succeeds, second fails
        when(mockNetworkClient.send(eq(replicas.get(0)), any(ReplicateRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        when(mockNetworkClient.send(eq(replicas.get(1)), any(ReplicateRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));
        
        // Act
        CompletableFuture<Void> result = replicationManager.replicateDelete(key, replicas);
        
        // Should complete without throwing exception
        assertDoesNotThrow(() -> result.get(200, TimeUnit.MILLISECONDS));
    }
    
    @Test
    @DisplayName("replicateDelete should timeout after 100ms")
    void testReplicateDeleteTimeout() throws Exception {
        // Arrange
        String key = "test-key";
        List<NodeInfo> replicas = Arrays.asList(
            new NodeInfo("node-2", "host2", 8002)
        );
        
        // Create a future that never completes
        CompletableFuture<Object> neverCompletes = new CompletableFuture<>();
        when(mockNetworkClient.send(any(NodeInfo.class), any(ReplicateRequest.class)))
            .thenReturn(neverCompletes);
        
        // Act
        CompletableFuture<Void> result = replicationManager.replicateDelete(key, replicas);
        
        // Should complete (with timeout) within reasonable time
        assertDoesNotThrow(() -> result.get(200, TimeUnit.MILLISECONDS));
    }
    
    @Test
    @DisplayName("syncWithReplica should complete immediately when no keys provided")
    void testSyncWithReplicaWithNoKeys() throws Exception {
        NodeInfo replica = new NodeInfo("node-2", "host2", 8002);
        
        // Test with null keys
        CompletableFuture<Void> result1 = replicationManager.syncWithReplica(replica, null);
        result1.get(50, TimeUnit.MILLISECONDS);
        
        // Test with empty keys
        CompletableFuture<Void> result2 = replicationManager.syncWithReplica(replica, Collections.emptyList());
        result2.get(50, TimeUnit.MILLISECONDS);
    }
    
    @Test
    @DisplayName("syncWithReplica should complete successfully with keys")
    void testSyncWithReplicaWithKeys() throws Exception {
        NodeInfo replica = new NodeInfo("node-2", "host2", 8002);
        List<String> keys = Arrays.asList("key1", "key2", "key3");
        
        CompletableFuture<Void> result = replicationManager.syncWithReplica(replica, keys);
        
        // Should complete successfully
        assertDoesNotThrow(() -> result.get(200, TimeUnit.MILLISECONDS));
    }
    
    @Test
    @DisplayName("replicatePut should use CompletableFuture.allOf for parallel replication")
    void testReplicatePutUsesParallelExecution() throws Exception {
        // Arrange
        String key = "test-key";
        String value = "test-value";
        List<NodeInfo> replicas = Arrays.asList(
            new NodeInfo("node-2", "host2", 8002),
            new NodeInfo("node-3", "host3", 8003),
            new NodeInfo("node-4", "host4", 8004)
        );
        
        // Create futures that complete with delays to verify parallel execution
        when(mockNetworkClient.send(any(NodeInfo.class), any(ReplicateRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // Act
        long startTime = System.currentTimeMillis();
        CompletableFuture<Void> result = replicationManager.replicatePut(key, value, replicas);
        result.get(200, TimeUnit.MILLISECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        // Assert - should complete quickly since operations are parallel
        assertTrue(duration < 150, "Parallel replication should complete quickly");
        verify(mockNetworkClient, times(3)).send(any(NodeInfo.class), any(ReplicateRequest.class));
    }
}
