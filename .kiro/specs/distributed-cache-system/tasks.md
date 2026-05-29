# Implementation Plan: Distributed Cache System

## Overview

This implementation plan breaks down the distributed cache system into discrete coding tasks. The system will be built incrementally, starting with core data structures and interfaces, then adding local caching functionality, distributed coordination, replication, and finally client APIs. Each task builds on previous work, with checkpoints to validate progress. Property-based tests are included to verify correctness properties from the design document.

## Tasks

- [x] 1. Set up project structure and core interfaces
  - Create Maven project structure with proper package organization
  - Define all core interfaces from design document (CacheClient, CacheNode, HashRing, LocalCache, EvictionPolicy, ReplicationManager, HealthMonitor, NetworkServer, NetworkClient, MessageSerializer, ConfigurationManager, MetricsCollector)
  - Define data model classes (CacheEntry, NodeInfo, NodeAddress, Message, MessageType, HealthStatus, EvictionPolicyType)
  - Define exception hierarchy (CacheException, InvalidKeyException, InvalidValueException, ConfigurationException, NetworkException, TimeoutException, SerializationException, NodeUnavailableException, ReplicationException)
  - Add Maven dependencies (JUnit 5, jqwik, SLF4J, Logback)
  - Create shared jqwik generators in test package (CacheArbitraries.java)
  - _Requirements: 1.5, 1.6, 3.4, 4.1, 4.5, 5.2, 7.3, 9.6, 9.7_

- [x] 2. Implement serialization and configuration management
  - [x] 2.1 Implement MessageSerializer with Java serialization
    - Create JavaMessageSerializer implementing MessageSerializer interface
    - Implement serialize() method to convert objects to byte arrays
    - Implement deserialize() method to convert byte arrays to objects
    - Implement estimateSize() method for memory estimation
    - Add error handling for non-serializable objects
    - _Requirements: 8.1, 8.2, 8.4, 8.5_
  
  - [x] 2.2 Write property test for serialization round trip
    - **Property 16: Serialization Round Trip Preserves Equivalence**
    - **Validates: Requirements 8.3**
  
  - [x] 2.3 Implement ConfigurationManager
    - Create CacheConfiguration class with all fields and validation constants
    - Implement ConfigurationManager with load(), save(), validate(), and prettyPrint() methods
    - Use JSON or properties format for configuration files
    - Implement validation logic for capacity and health check interval bounds
    - _Requirements: 9.1, 9.2, 9.3, 9.5, 9.6, 9.7_
  
  - [x] 2.4 Write property test for configuration round trip
    - **Property 17: Configuration Round Trip Preserves Equivalence**
    - **Validates: Requirements 9.4**
  
  - [x] 2.5 Write property test for configuration validation
    - **Property 18: Configuration Validation Enforces Bounds**
    - **Validates: Requirements 9.6, 9.7**

- [x] 3. Implement consistent hashing with virtual nodes
  - [x] 3.1 Implement HashRing with TreeMap-based consistent hashing
    - Create ConsistentHashRing implementing HashRing interface
    - Use TreeMap<Long, NodeInfo> for ring storage
    - Implement hash function (e.g., MurmurHash3 or SHA-256)
    - Implement addNode() to create 150 virtual nodes per physical node
    - Implement removeNode() to remove all virtual nodes and return affected keys
    - Implement getPrimaryNode() using TreeMap.ceilingEntry() with wrap-around
    - Implement getReplicaNodes() to return N consecutive nodes in ring order
    - Implement getAllNodes() and hash() methods
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  
  - [x] 3.2 Write property test for deterministic key-to-node mapping
    - **Property 4: Hash Ring Maps Each Key to Exactly One Primary Node**
    - **Validates: Requirements 2.1**
  
  - [x] 3.3 Write property test for hash ring idempotence
    - **Property 5: Hash Ring Mapping is Idempotent**
    - **Validates: Requirements 2.5**
  
  - [x] 3.4 Write property test for node addition redistribution
    - **Property 6: Node Addition Redistributes Only Affected Keys**
    - **Validates: Requirements 2.2**
  
  - [x] 3.5 Write property test for node removal redistribution
    - **Property 7: Node Removal Redistributes Only That Node's Keys**
    - **Validates: Requirements 2.3**

- [x] 4. Implement eviction policies
  - [x] 4.1 Implement LRU eviction policy
    - Create LRUEvictionPolicy implementing EvictionPolicy interface
    - Use LinkedHashMap with access-order for O(1) LRU tracking
    - Implement onAccess() to update access order
    - Implement onPut() and onRemove() to maintain metadata
    - Implement selectVictims() to select least recently used entries
    - Use ReadWriteLock for thread safety
    - _Requirements: 4.2_
  
  - [x] 4.2 Implement LFU eviction policy
    - Create LFUEvictionPolicy implementing EvictionPolicy interface
    - Track access frequency for each key
    - Implement onAccess() to increment access count
    - Implement selectVictims() to select least frequently used entries
    - Use thread-safe data structures
    - _Requirements: 4.3_
  
  - [x] 4.3 Implement FIFO eviction policy
    - Create FIFOEvictionPolicy implementing EvictionPolicy interface
    - Track insertion order using LinkedHashMap or queue
    - Implement selectVictims() to select oldest entries by creation time
    - _Requirements: 4.4_
  
  - [x] 4.4 Write property test for LRU eviction correctness
    - **Property 11: LRU Eviction Removes Least Recently Used Entry**
    - **Validates: Requirements 4.2**
  
  - [x] 4.5 Write property test for LFU eviction correctness
    - **Property 12: LFU Eviction Removes Least Frequently Used Entry**
    - **Validates: Requirements 4.3**
  
  - [x] 4.6 Write property test for FIFO eviction correctness
    - **Property 13: FIFO Eviction Removes Oldest Entry**
    - **Validates: Requirements 4.4**
  
  - [x] 4.7 Write property test for eviction space freeing
    - **Property 14: Eviction Frees Sufficient Space**
    - **Validates: Requirements 4.1, 4.5**

- [x] 5. Implement local cache with eviction
  - [x] 5.1 Implement LocalCache with ConcurrentHashMap
    - Create LocalCacheImpl implementing LocalCache interface
    - Use ConcurrentHashMap<String, CacheEntry<V>> for storage
    - Use AtomicLong for memory usage tracking
    - Implement put() with eviction trigger at 95% capacity
    - Implement get() with access tracking for eviction policy
    - Implement remove(), size(), getMemoryUsage(), getCapacity(), getMemoryUsagePercentage(), clear()
    - Integrate with EvictionPolicy via setEvictionPolicy()
    - Use ReadWriteLock for eviction coordination
    - Validate key size (max 256 bytes) and value size (max 1 MB)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 4.1, 4.5_
  
  - [x] 5.2 Write property test for cache put-get round trip
    - **Property 1: Cache Put-Get Round Trip**
    - **Validates: Requirements 1.1, 1.2, 1.5, 1.6**
  
  - [x] 5.3 Write property test for non-existent key retrieval
    - **Property 2: Cache Get Returns Not-Found for Non-Existent Keys**
    - **Validates: Requirements 1.3**
  
  - [x] 5.4 Write property test for cache delete
    - **Property 3: Cache Delete Removes Entry**
    - **Validates: Requirements 1.4**
  
  - [x] 5.5 Write unit tests for edge cases
    - Test exactly 256-byte keys (boundary)
    - Test exactly 1 MB values (boundary)
    - Test empty values
    - Test key size validation (> 256 bytes rejected)
    - Test value size validation (> 1 MB rejected)
    - _Requirements: 1.5, 1.6_

- [x] 6. Checkpoint - Validate local cache functionality
  - Ensure all tests pass for local cache, eviction policies, and serialization
  - Verify that cache operations meet latency requirements (< 10ms for get)
  - Ask the user if questions arise

- [x] 7. Implement network communication layer
  - [x] 7.1 Implement Message classes and MessageType enum
    - Create concrete message classes extending Message base class
    - Implement GetRequest, GetResponse, PutRequest, PutResponse, DeleteRequest, DeleteResponse
    - Implement ReplicateRequest, HealthCheck, HealthResponse, NodeJoin, NodeLeave, Rebalance messages
    - Ensure all messages are Serializable
    - _Requirements: 7.1, 7.4_
  
  - [x] 7.2 Implement NetworkServer with Java NIO
    - Create NetworkServerImpl implementing NetworkServer interface
    - Use Java NIO (SocketChannel, Selector) for non-blocking I/O
    - Implement start() to bind to configured port
    - Implement stop() to close server socket
    - Implement registerHandler() to map MessageType to MessageHandler
    - Handle incoming connections and deserialize messages
    - Route messages to registered handlers
    - Support concurrent connections
    - _Requirements: 7.1, 7.6, 11.5_
  
  - [x] 7.3 Implement NetworkClient with retry logic
    - Create NetworkClientImpl implementing NetworkClient interface
    - Implement send() to send messages to remote nodes
    - Implement sendWithRetry() with exponential backoff (100ms, 200ms, 400ms)
    - Implement broadcast() to send messages to all nodes
    - Use CompletableFuture for asynchronous operations
    - Handle connection timeouts and failures
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [x] 7.4 Write property test for network retry logic
    - **Property 27: Network Retry Logic Attempts Up To 3 Retries**
    - **Validates: Requirements 7.3**
  
  - [x] 7.5 Write unit tests for network layer
    - Test message serialization and deserialization
    - Test connection timeout handling
    - Test concurrent connections
    - Test network latency (< 20ms under normal conditions)
    - _Requirements: 7.2, 7.6_

- [x] 8. Implement health monitoring
  - [x] 8.1 Implement HealthMonitor with scheduled health checks
    - Create HealthMonitorImpl implementing HealthMonitor interface
    - Use ScheduledExecutorService for periodic health checks (every 5 seconds)
    - Track consecutive failure count per node
    - Implement start() and stop() for lifecycle management
    - Implement onStatusChange() for callback registration
    - Implement getNodeStatus() to return current health status
    - Implement getHealthyNodes() to filter available nodes
    - Implement checkNode() for manual health checks
    - Mark node unavailable after 3 consecutive failures
    - Mark node healthy when it responds after being unavailable
    - Log all status changes with timestamps
    - _Requirements: 5.1, 5.2, 5.4, 5.5_
  
  - [x] 8.2 Write property test for failure detection
    - **Property 30: Health Monitor Marks Nodes Unavailable After Consecutive Failures**
    - **Validates: Requirements 5.2**
  
  - [x] 8.3 Write unit tests for health monitoring
    - Test health check interval (5 seconds)
    - Test status change callbacks
    - Test node recovery detection
    - Test logging of status changes
    - _Requirements: 5.1, 5.4, 5.5_

- [x] 9. Implement replication manager
  - [x] 9.1 Implement ReplicationManager with async replication
    - Create ReplicationManagerImpl implementing ReplicationManager interface
    - Inject NetworkClient for inter-node communication
    - Implement replicatePut() to send PUT requests to replica nodes
    - Implement replicateDelete() to send DELETE requests to replica nodes
    - Implement syncWithReplica() for data synchronization
    - Use CompletableFuture.allOf() to wait for all replicas (with timeout)
    - Set replication timeout to 100ms
    - Support replication factor between 1 and 5
    - Handle replication failures gracefully (log and continue)
    - _Requirements: 3.1, 3.2, 3.4, 3.5_
  
  - [x] 9.2 Write property test for replication count
    - **Property 8: Replication Maintains Correct Number of Copies**
    - **Validates: Requirements 3.1, 3.2, 3.4**
  
  - [x] 9.3 Write property test for replica failover
    - **Property 9: Replicas Serve Data When Primary Fails**
    - **Validates: Requirements 3.3**
  
  - [x] 9.4 Write property test for replica update propagation
    - **Property 10: Replica Updates Are Propagated**
    - **Validates: Requirements 3.5**
  
  - [x] 9.5 Write property test for eviction across replicas
    - **Property 15: Eviction Removes From All Replicas**
    - **Validates: Requirements 4.6**
  
  - [x] 9.6 Write unit tests for replication
    - Test replication timeout (100ms)
    - Test replication factor validation (1-5)
    - Test replication failure handling
    - _Requirements: 3.2, 3.4_

- [x] 10. Implement metrics collection
  - [x] 10.1 Implement MetricsCollector with atomic counters
    - Create MetricsCollectorImpl implementing MetricsCollector interface
    - Use AtomicLong for hit/miss counters
    - Use LongAdder for latency accumulation
    - Implement recordHit(), recordMiss(), recordGetLatency(), recordMemoryUsage()
    - Implement getMetrics() to return snapshot of current metrics
    - Calculate hits/misses per second using sliding window
    - Calculate average latency
    - Update metrics every 1 second
    - Implement reset() to clear all metrics
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.6_
  
  - [x] 10.2 Write unit tests for metrics collection
    - Test hit/miss counting accuracy
    - Test latency calculation
    - Test memory usage percentage calculation
    - Test metrics update frequency (1 second)
    - Test metrics reset
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.6_

- [x] 11. Checkpoint - Validate distributed components
  - Ensure all tests pass for network layer, health monitoring, replication, and metrics
  - Verify that replication meets latency requirements (< 100ms)
  - Ask the user if questions arise

- [x] 12. Implement CacheNode with all components
  - [x] 12.1 Implement CacheNode integrating all components
    - Create CacheNodeImpl implementing CacheNode interface
    - Inject LocalCache, HashRing, ReplicationManager, HealthMonitor, NetworkServer, NetworkClient, MetricsCollector, ConfigurationManager
    - Implement start() to initialize all components and join cluster
    - Implement stop() to gracefully leave cluster and shutdown components
    - Implement handleGet() to retrieve from local cache and update metrics
    - Implement handlePut() to store locally, replicate, and update metrics
    - Implement handleDelete() to remove locally, replicate, and update metrics
    - Implement getNodeId() and getAddress() for node identification
    - Implement getMetrics() and getHealthStatus() for observability
    - Register message handlers for inter-node communication
    - Integrate with HashRing to determine primary/replica nodes
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 3.1, 3.5, 11.1, 11.2, 11.3, 11.4_
  
  - [x] 12.2 Write property test for concurrent writes to different keys
    - **Property 19: Concurrent Writes to Different Keys Preserve All Data**
    - **Validates: Requirements 11.2**
  
  - [x] 12.3 Write property test for concurrent writes to same key
    - **Property 20: Concurrent Writes to Same Key Produce Consistent State**
    - **Validates: Requirements 11.3**
  
  - [x] 12.4 Write property test for concurrent reads during writes
    - **Property 21: Concurrent Reads During Writes Return Valid Data**
    - **Validates: Requirements 11.4**
  
  - [x] 12.5 Write unit tests for CacheNode
    - Test node startup and shutdown
    - Test message handler registration
    - Test metrics integration
    - Test concurrent client connections (1000+)
    - _Requirements: 11.5_

- [x] 13. Implement node discovery and cluster membership
  - [x] 13.1 Implement node discovery with broadcast
    - Add node discovery logic to CacheNode
    - Implement broadcast of NODE_JOIN message on startup
    - Implement handler for NODE_JOIN messages to add nodes to HashRing
    - Implement handler for NODE_LEAVE messages to remove nodes from HashRing
    - Maintain list of known nodes in memory
    - Persist node list to disk for restart recovery
    - Implement rebalancing logic when nodes join/leave
    - Trigger rebalancing within 30 seconds of topology change
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_
  
  - [x] 13.2 Write property test for cluster membership accuracy
    - **Property 28: Node Discovery Maintains Accurate Cluster Membership**
    - **Validates: Requirements 10.2, 10.3**
  
  - [x] 13.3 Write property test for node list persistence
    - **Property 29: Node List Persistence Survives Restarts**
    - **Validates: Requirements 10.5**
  
  - [x] 13.4 Write unit tests for node discovery
    - Test node join broadcast
    - Test node list persistence and recovery
    - Test rebalancing timing (within 30 seconds)
    - _Requirements: 10.1, 10.4_

- [x] 14. Implement hash ring integration with health monitoring
  - [x] 14.1 Integrate HashRing with HealthMonitor
    - Modify HashRing to filter out unavailable nodes
    - Register health status change callback in CacheNode
    - Update HashRing when nodes become unavailable/available
    - Ensure getPrimaryNode() and getReplicaNodes() exclude unavailable nodes
    - _Requirements: 5.3, 5.4_
  
  - [x] 14.2 Write property test for routing exclusion of failed nodes
    - **Property 22: Failed Nodes Are Excluded From Routing**
    - **Validates: Requirements 5.3**
  
  - [x] 14.3 Write property test for routing inclusion of recovered nodes
    - **Property 23: Recovered Nodes Are Included in Routing**
    - **Validates: Requirements 5.4**

- [x] 15. Checkpoint - Validate multi-node cluster
  - Set up integration test with 3-5 cache nodes
  - Test node discovery and cluster formation
  - Test data distribution across nodes
  - Test replication across nodes
  - Test node failure and recovery
  - Ask the user if questions arise

- [x] 16. Implement CacheClient API
  - [x] 16.1 Implement CacheClient with routing and failover
    - Create CacheClientImpl implementing CacheClient interface
    - Inject NetworkClient and maintain local copy of HashRing
    - Implement get() to route request to primary node via HashRing
    - Implement put() to route request to primary node
    - Implement delete() to route request to primary node
    - Implement batchGet() to batch requests by target node
    - Implement getAsync(), putAsync(), deleteAsync() with CompletableFuture
    - Implement retry logic with failover to replica nodes (within 50ms)
    - Handle node unavailability by trying replicas
    - Implement close() to cleanup resources
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  
  - [x] 16.2 Write property test for client routing correctness
    - **Property 24: Client Routes to Correct Node**
    - **Validates: Requirements 6.4**
  
  - [x] 16.3 Write property test for client failover to replicas
    - **Property 25: Client Retries on Replica When Primary Unavailable**
    - **Validates: Requirements 6.5**
  
  - [x] 16.4 Write property test for batch get correctness
    - **Property 26: Batch Get Returns Correct Values for All Keys**
    - **Validates: Requirements 6.6**
  
  - [x] 16.5 Write unit tests for CacheClient
    - Test API method signatures and contracts
    - Test retry latency (< 50ms)
    - Test async operations
    - Test error handling
    - _Requirements: 6.1, 6.2, 6.3, 6.5_

- [x] 17. Implement monitoring endpoint
  - [x] 17.1 Add HTTP endpoint for metrics exposure
    - Create simple HTTP server for metrics endpoint (e.g., using com.sun.net.httpserver)
    - Expose metrics at /metrics endpoint
    - Return JSON representation of CacheMetrics
    - Include hits per second, misses per second, average latency, memory usage percentage
    - _Requirements: 12.5_
  
  - [x] 17.2 Write unit tests for monitoring endpoint
    - Test endpoint availability
    - Test JSON format
    - Test metrics accuracy
    - _Requirements: 12.5_

- [-] 18. Final integration and end-to-end testing
  - [-] 18.1 Write integration tests for complete system
    - Test multi-node cluster with client operations
    - Test node failure during client operations
    - Test automatic failover to replicas
    - Test node recovery and rejoin
    - Test data rebalancing after topology changes
    - Test eviction across replicas
    - Test concurrent client operations
    - _Requirements: All_
  
  - [ ] 18.2 Write performance tests
    - Test get operation latency (< 10ms)
    - Test replication latency (< 100ms)
    - Test network message delivery (< 20ms)
    - Test client retry latency (< 50ms)
    - Test concurrent connection capacity (1000+)
    - _Requirements: 1.2, 3.2, 7.2, 6.5, 11.5_

- [ ] 19. Final checkpoint - Complete system validation
  - Run all unit tests, property tests, and integration tests
  - Verify all 30 correctness properties pass
  - Verify all performance requirements are met
  - Verify code coverage meets goals (80% line coverage, 75% branch coverage)
  - Ask the user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Property-based tests validate universal correctness properties from the design document
- Unit tests validate specific examples, edge cases, and performance requirements
- Integration tests validate end-to-end scenarios and multi-node behavior
- The system uses Java 17 with Maven for build management
- jqwik is used for property-based testing with minimum 100 iterations per property
- All property tests include comments linking to design document properties
- Checkpoints ensure incremental validation and provide opportunities for user feedback

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1"] },
    { "id": 1, "tasks": ["2.1", "2.3"] },
    { "id": 2, "tasks": ["2.2", "2.4", "2.5", "3.1", "4.1", "4.2", "4.3"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4", "3.5", "4.4", "4.5", "4.6", "4.7", "5.1"] },
    { "id": 4, "tasks": ["5.2", "5.3", "5.4", "5.5"] },
    { "id": 5, "tasks": ["7.1"] },
    { "id": 6, "tasks": ["7.2", "7.3"] },
    { "id": 7, "tasks": ["7.4", "7.5", "8.1", "10.1"] },
    { "id": 8, "tasks": ["8.2", "8.3", "9.1", "10.2"] },
    { "id": 9, "tasks": ["9.2", "9.3", "9.4", "9.5", "9.6", "12.1"] },
    { "id": 10, "tasks": ["12.2", "12.3", "12.4", "12.5", "13.1"] },
    { "id": 11, "tasks": ["13.2", "13.3", "13.4", "14.1"] },
    { "id": 12, "tasks": ["14.2", "14.3"] },
    { "id": 13, "tasks": ["16.1"] },
    { "id": 14, "tasks": ["16.2", "16.3", "16.4", "16.5", "17.1"] },
    { "id": 15, "tasks": ["17.2", "18.1"] },
    { "id": 16, "tasks": ["18.2"] }
  ]
}
```
