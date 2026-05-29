# Distributed Cache System

A distributed caching system built with Java 17 and Maven, featuring consistent hashing, replication, and eviction policies.

## Project Structure

```
distributed-cache-system/
├── src/
│   ├── main/
│   │   ├── java/com/distributedcache/
│   │   │   ├── cache/          # Core cache implementation
│   │   │   ├── network/        # Network communication layer
│   │   │   ├── hashing/        # Consistent hashing implementation
│   │   │   ├── eviction/       # Cache eviction policies
│   │   │   ├── replication/    # Data replication logic
│   │   │   ├── node/           # Cache node management
│   │   │   ├── client/         # Client API
│   │   │   └── utils/          # Utility classes
│   │   └── resources/          # Configuration files
│   └── test/
│       ├── java/               # Test classes
│       └── resources/          # Test resources
└── pom.xml                     # Maven configuration
```

## Package Purposes

### `cache`
Core cache implementation including the main cache data structure, cache operations (get, put, delete), and cache entry management. This is the heart of the caching system.

### `network`
Network communication layer handling inter-node communication, request/response protocols, connection management, and message serialization/deserialization.

### `hashing`
Consistent hashing implementation for distributing keys across cache nodes. Includes hash ring management, virtual nodes, and key-to-node mapping logic.

### `eviction`
Cache eviction policies (LRU, LFU, FIFO, etc.) to manage cache size and remove stale entries. Implements different strategies for cache entry removal when capacity is reached.

### `replication`
Data replication logic for fault tolerance and high availability. Handles replication factor configuration, replica synchronization, and consistency management.

### `node`
Cache node management including node lifecycle, health monitoring, node discovery, and cluster membership management.

### `client`
Client API for interacting with the distributed cache. Provides simple interfaces for applications to perform cache operations without worrying about distribution details.

### `utils`
Utility classes for common operations like serialization, logging helpers, configuration parsing, and other shared functionality.

## Requirements

- Java 17 or higher
- Maven 3.6 or higher

## Build

```bash
mvn clean install
```

## Test

```bash
mvn test
```
