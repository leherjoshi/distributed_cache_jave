# 📚 Distributed Cache System - Complete Beginner's Guide

## 🎯 What is This Project?

Imagine you have a website with millions of users. Every time someone wants to see their profile, you fetch data from a database. But databases are slow! 

**A cache is like a super-fast notepad** that stores frequently-used data so you don't have to go to the slow database every time.

**A distributed cache** means this "super-fast notepad" is spread across multiple computers (nodes), so:
- If one computer dies, your data is still safe on other computers
- You can handle millions of requests because multiple computers share the work
- Data is automatically distributed evenly across all computers

---

## 🏗️ Project Structure Overview

```
distributed-cache-system/
├── src/main/java/com/distributedcache/    # Main code
│   ├── cache/          # Local storage on each computer
│   ├── client/         # How users interact with the cache
│   ├── eviction/       # Rules for removing old data
│   ├── exceptions/     # Error handling
│   ├── hashing/        # How to decide which computer stores what
│   ├── monitoring/     # Health checks and statistics
│   ├── network/        # How computers talk to each other
│   ├── node/           # Each computer in the cluster
│   ├── replication/    # Copying data for backup
│   ├── utils/          # Helper tools
│   └── demo/           # Demo application
├── src/test/java/      # Tests to verify everything works
├── pom.xml             # Project configuration (dependencies)
└── README.md           # Project documentation
```

---

## 📁 File-by-File Explanation

### **1. CACHE PACKAGE** (`src/main/java/com/distributedcache/cache/`)

Think of this as the **actual storage box** where data lives.

#### **CacheEntry.java**
```java
// What it does: Wraps your data with extra information
// Think of it like: A labeled box with your data inside

Key parts:
- value: The actual data you stored
- createdAt: When you stored it
- lastAccessedAt: When someone last looked at it
- accessCount: How many times it's been used
```

**Real-world analogy:** Like a file in your computer that has the actual content PLUS metadata (created date, last modified, size).

#### **LocalCache.java** (Interface)
```java
// What it does: Defines what a cache MUST be able to do
// Think of it like: A contract or blueprint

Must be able to:
- put(key, value): Store data
- get(key): Retrieve data
- remove(key): Delete data
- size(): Count items
- clear(): Delete everything
```

**Real-world analogy:** Like a job description that says "A cashier must be able to scan items, handle money, and give receipts."

#### **LocalCacheImpl.java** (Implementation)
```java
// What it does: The ACTUAL working cache on one computer
// Think of it like: The real storage system

Key features:
- Uses ConcurrentHashMap: Thread-safe storage (multiple people can use it at once)
- Tracks memory usage: Knows how full it is
- Automatic eviction: Deletes old data when getting full (at 95% capacity)
- Validates data: Checks key size (max 256 bytes) and value size (max 1 MB)
```

**How it works:**
1. You call `put("user123", "John Doe")`
2. It wraps "John Doe" in a CacheEntry
3. Stores it in a ConcurrentHashMap with key "user123"
4. Checks if cache is 95% full → if yes, deletes old entries
5. Updates memory usage counter

**Real-world analogy:** Like your phone's photo gallery - stores photos, shows storage usage, auto-deletes old photos when storage is full.

---

### **2. CLIENT PACKAGE** (`src/main/java/com/distributedcache/client/`)

This is how **users interact** with the distributed cache.

#### **CacheClient.java** (Interface)
```java
// What it does: Defines how users can use the cache
// Think of it like: The buttons on your TV remote

Can do:
- get(key): Get data
- put(key, value): Store data
- delete(key): Remove data
- batchGet(keys): Get multiple items at once
- getAsync(), putAsync(): Do operations without waiting
```

#### **CacheClientImpl.java** (Implementation)
```java
// What it does: The actual implementation that routes requests

Key features:
- Smart routing: Figures out which computer has your data
- Automatic failover: If one computer is down, tries another
- Batch operations: Can get multiple items efficiently
- Async operations: Can do things in the background
```

**How it works:**
1. You call `client.get("user123")`
2. It uses the hash ring to find which node has "user123"
3. Sends a network request to that node
4. If that node is down, tries a replica node
5. Returns the value to you

**Real-world analogy:** Like ordering food on Uber Eats - you place an order, it figures out which restaurant to send it to, if one restaurant is closed it suggests another, and delivers the food to you.

---

### **3. EVICTION PACKAGE** (`src/main/java/com/distributedcache/eviction/`)

When the cache is full, **which items should be deleted?** This package decides.

#### **EvictionPolicy.java** (Interface)
```java
// What it does: Defines how to choose items to delete

Must provide:
- onAccess(key): Note that someone used this item
- onPut(key, entry): Note that we stored this item
- onRemove(key): Note that we deleted this item
- selectVictims(count): Choose which items to delete
```

#### **LRUEvictionPolicy.java** (Least Recently Used)
```java
// What it does: Deletes items that haven't been used in the longest time

Strategy: Keep frequently-used items, delete old ones

Example:
- You access: A, B, C, A, B, D
- Order: D → C → B → A (A is most recent)
- If full, delete D first (least recently used)
```

**Real-world analogy:** Netflix removing movies you haven't watched in years.

#### **LFUEvictionPolicy.java** (Least Frequently Used)
```java
// What it does: Deletes items that are used the least

Strategy: Keep popular items, delete unpopular ones

Example:
- Access counts: A=10, B=5, C=2, D=1
- If full, delete D first (used only once)
```

**Real-world analogy:** A store removing products that nobody buys.

#### **FIFOEvictionPolicy.java** (First In First Out)
```java
// What it does: Deletes oldest items first

Strategy: Like a queue - first stored, first removed

Example:
- Store: A, B, C, D
- If full, delete A first (oldest)
```

**Real-world analogy:** A milk carton expiring - use the oldest one first.

---

### **4. EXCEPTIONS PACKAGE** (`src/main/java/com/distributedcache/exceptions/`)

**Error handling** - what happens when things go wrong?

#### **CacheException.java**
```java
// Base exception for all cache-related errors
// Like: The parent of all errors
```

#### **InvalidKeyException.java**
```java
// Thrown when: Key is too long (> 256 bytes) or null
// Example: put(null, "value") → InvalidKeyException
```

#### **InvalidValueException.java**
```java
// Thrown when: Value is too large (> 1 MB) or null
// Example: put("key", 2MB_file) → InvalidValueException
```

#### **NetworkException.java**
```java
// Thrown when: Can't connect to other nodes
// Example: Node is down, network is broken
```

#### **TimeoutException.java**
```java
// Thrown when: Operation takes too long
// Example: Waiting for replication > 100ms → TimeoutException
```

**Real-world analogy:** Like error messages on your phone - "No Internet Connection", "Storage Full", "App Not Responding".

---

### **5. HASHING PACKAGE** (`src/main/java/com/distributedcache/hashing/`)

**How to decide which computer stores which data?**

#### **HashRing.java** (Interface)
```java
// What it does: Maps keys to nodes (computers)

Must provide:
- addNode(node): Add a computer to the cluster
- removeNode(nodeId): Remove a computer
- getPrimaryNode(key): Which computer stores this key?
- getReplicaNodes(key, count): Which computers have backups?
```

#### **ConsistentHashRing.java** (Implementation)
```java
// What it does: Uses consistent hashing algorithm

Key concept:
- Imagine a circle with numbers 0 to 2^32
- Each node is placed at multiple points on the circle (150 virtual nodes)
- Each key is hashed to a number on the circle
- The key goes to the nearest node clockwise

Why 150 virtual nodes per physical node?
- Ensures even distribution of data
- When a node leaves, its data spreads evenly to other nodes
```

**Visual Example:**
```
Circle (0 → 2^32 → 0):
    
       Node A (virtual nodes at 100, 500, 900...)
      ○
    ○   ○    Key "user123" hashes to 450
  ○       ○  → Goes to nearest node clockwise
○    Key    ○ → Node A (at position 500)
  ○       ○
    ○   ○
      ○
       Node B (virtual nodes at 200, 600, 1000...)
```

**Real-world analogy:** Like a circular track where runners (nodes) are spread around, and you throw a ball (key) - it goes to the nearest runner.

#### **NodeInfo.java**
```java
// What it does: Stores information about a node

Contains:
- nodeId: Unique name (e.g., "node-1")
- host: IP address (e.g., "localhost")
- port: Port number (e.g., 8001)
```

#### **NodeAddress.java**
```java
// What it does: Stores network address

Contains:
- host: Where the node is (e.g., "192.168.1.10")
- port: Which port to connect to (e.g., 8001)
```

**Real-world analogy:** Like a mailing address - host is the street, port is the apartment number.

---

### **6. MONITORING PACKAGE** (`src/main/java/com/distributedcache/monitoring/`)

**Health checks and statistics.**

#### **MetricsServer.java**
```java
// What it does: Creates an HTTP server to show statistics

How it works:
- Starts a web server on port 9001
- When you visit http://localhost:9001/metrics
- Returns JSON with statistics:
  {
    "hitsPerSecond": 12.5,
    "missesPerSecond": 0.8,
    "averageGetLatencyMs": 1.23,
    "memoryUsagePercentage": 0.05,
    "totalHits": 150,
    "totalMisses": 10
  }
```

**Real-world analogy:** Like your phone's battery stats page - shows usage, remaining capacity, charging speed.

---

### **7. NETWORK PACKAGE** (`src/main/java/com/distributedcache/network/`)

**How computers talk to each other.**

#### **Message.java** (Base class)
```java
// What it does: Base for all messages between nodes

Contains:
- messageId: Unique ID for tracking
- senderId: Who sent this message
- timestamp: When it was sent
```

#### **Message Types:**

1. **GetRequest.java** / **GetResponse.java**
   ```java
   // Request: "Give me the value for key 'user123'"
   // Response: "Here it is: 'John Doe'" or "Not found"
   ```

2. **PutRequest.java** / **PutResponse.java**
   ```java
   // Request: "Store 'user123' = 'John Doe'"
   // Response: "Done" or "Failed"
   ```

3. **DeleteRequest.java** / **DeleteResponse.java**
   ```java
   // Request: "Delete 'user123'"
   // Response: "Deleted" or "Not found"
   ```

4. **HealthCheck.java** / **HealthResponse.java**
   ```java
   // Request: "Are you alive?"
   // Response: "Yes, I'm healthy"
   ```

5. **NodeJoin.java** / **NodeLeave.java**
   ```java
   // NodeJoin: "Hi everyone, I'm joining the cluster!"
   // NodeLeave: "Goodbye, I'm leaving the cluster!"
   ```

6. **ReplicateRequest.java**
   ```java
   // "Hey backup node, store this data too"
   ```

**Real-world analogy:** Like text messages between friends - "What's for dinner?" (GetRequest), "Pizza!" (GetResponse).

#### **NetworkServer.java** / **NetworkServerImpl.java**
```java
// What it does: Listens for incoming messages from other nodes

How it works:
- Opens a port (e.g., 8001)
- Waits for connections
- When a message arrives, routes it to the right handler
- Sends response back

Uses Java NIO for non-blocking I/O (can handle many connections at once)
```

**Real-world analogy:** Like a restaurant host - greets customers, takes them to tables, handles multiple customers simultaneously.

#### **NetworkClient.java** / **NetworkClientImpl.java**
```java
// What it does: Sends messages to other nodes

Features:
- send(node, message): Send a message
- sendWithRetry(node, message): Try up to 3 times if it fails
- broadcast(message): Send to all nodes

Retry strategy:
- Try 1: Send
- Wait 100ms
- Try 2: Send
- Wait 200ms
- Try 3: Send
- Give up after 3 tries
```

**Real-world analogy:** Like sending a package - if delivery fails, try again tomorrow, then 2 days later, then give up.

---

### **8. NODE PACKAGE** (`src/main/java/com/distributedcache/node/`)

**The actual computers (nodes) in the cluster.**

#### **CacheNode.java** (Interface)
```java
// What it does: Defines what a node must do

Must provide:
- start(): Join the cluster
- stop(): Leave the cluster
- handleGet(key): Process get request
- handlePut(key, value): Process put request
- handleDelete(key): Process delete request
- getMetrics(): Return statistics
- getHealthStatus(): Am I healthy?
```

#### **CacheNodeImpl.java** (Implementation)
```java
// What it does: The complete working node

Components it manages:
1. LocalCache: Stores data locally
2. HashRing: Knows where data should go
3. ReplicationManager: Copies data to backups
4. HealthMonitor: Checks if other nodes are alive
5. NetworkServer: Receives messages
6. NetworkClient: Sends messages
7. MetricsCollector: Tracks statistics

How it works when you store data:
1. Receive put("user123", "John Doe")
2. Store in local cache
3. Calculate replica nodes from hash ring
4. Tell ReplicationManager to copy to replicas
5. Update metrics (hits, latency, memory)
6. Return success
```

**Real-world analogy:** Like a post office branch - receives packages (requests), stores them locally, forwards copies to other branches (replication), tracks deliveries (metrics).

#### **HealthMonitor.java** / **HealthMonitorImpl.java**
```java
// What it does: Checks if other nodes are alive

How it works:
- Every 5 seconds, ping all known nodes
- If a node doesn't respond 3 times in a row → mark as dead
- If a dead node responds → mark as alive again
- Notify when status changes

Status tracking:
- Node responds: failureCount = 0, status = HEALTHY
- Node fails once: failureCount = 1, status = HEALTHY
- Node fails twice: failureCount = 2, status = HEALTHY
- Node fails 3 times: failureCount = 3, status = UNAVAILABLE
```

**Real-world analogy:** Like checking if your friends are online - send "ping" every 5 seconds, if they don't respond 3 times, mark as "offline".

#### **MetricsCollector.java** / **MetricsCollectorImpl.java**
```java
// What it does: Tracks statistics

Tracks:
- totalHits: How many successful gets
- totalMisses: How many failed gets
- hitsPerSecond: Current hit rate
- missesPerSecond: Current miss rate
- averageLatency: How long operations take
- memoryUsage: How much space is used

Updates every 1 second using a sliding window
```

**Real-world analogy:** Like a fitness tracker - counts steps, calculates calories burned, tracks heart rate.

---

### **9. REPLICATION PACKAGE** (`src/main/java/com/distributedcache/replication/`)

**Copying data to backup nodes for safety.**

#### **ReplicationManager.java** / **ReplicationManagerImpl.java**
```java
// What it does: Manages data replication

How it works:
1. You store "user123" = "John Doe"
2. Calculate which nodes should have backups (using hash ring)
3. Send ReplicateRequest to each backup node
4. Wait up to 100ms for all to respond
5. If some fail, log error but don't fail the whole operation

Replication factor:
- 1: No backups (dangerous!)
- 2: 1 backup (if primary dies, you have 1 copy)
- 3: 2 backups (recommended for production)
- 5: 4 backups (very safe but uses more space)

Uses CompletableFuture for async operations (don't wait, do multiple at once)
```

**Real-world analogy:** Like saving a document - Word auto-saves to OneDrive, you also save to USB drive, and email yourself a copy. If your computer crashes, you still have it!

---

### **10. UTILS PACKAGE** (`src/main/java/com/distributedcache/utils/`)

**Helper tools and utilities.**

#### **JavaMessageSerializer.java**
```java
// What it does: Converts objects to bytes (for network transfer)

Why needed?
- Can't send Java objects over network
- Must convert to bytes first

How it works:
- serialize(object): Object → byte[]
- deserialize(bytes): byte[] → Object
- estimateSize(object): Calculate memory usage

Uses Java's built-in serialization
```

**Real-world analogy:** Like converting a Word document to PDF before emailing - recipient can open it even without Word.

#### **CacheConfiguration.java**
```java
// What it does: Stores all configuration settings

Settings:
- cacheCapacityBytes: How much data to store (e.g., 10 MB)
- replicationFactor: How many backup copies (e.g., 3)
- evictionPolicy: Which eviction strategy (LRU/LFU/FIFO)
- healthCheckInterval: How often to ping nodes (e.g., 5 seconds)
- serverPort: Which port to use (e.g., 8001)
- seedNodes: Initial nodes to connect to
```

#### **ConfigurationManager.java** / **PropertiesConfigurationManager.java**
```java
// What it does: Loads and saves configuration

Can:
- load(): Read config from file
- save(): Write config to file
- validate(): Check if config is valid

File format (properties):
cache.capacity=10485760
replication.factor=3
eviction.policy=LRU
health.check.interval=5
```

**Real-world analogy:** Like settings on your phone - load saved settings when starting, save when you change something.

---

### **11. DEMO PACKAGE** (`src/main/java/com/distributedcache/demo/`)

#### **QuickStartDemo.java**
```java
// What it does: Demonstrates the entire system

Steps:
1. Start 3 nodes (ports 8001, 8002, 8003)
2. Start metrics server (port 9001)
3. Create a client
4. Store 5 key-value pairs
5. Retrieve all values
6. Delete one value
7. Show metrics for each node
8. Stop one node (simulate failure)
9. Try to retrieve data (shows failover)
10. Keep running so you can see metrics

Run with: ./run-demo.sh
```

---

## 🧪 TEST PACKAGE (`src/test/java/`)

**Tests to verify everything works correctly.**

### **Test Categories:**

#### **1. Unit Tests** (Test individual components)
- `LocalCacheImplTest`: Test cache storage
- `LRUEvictionPolicyTest`: Test LRU eviction
- `ConsistentHashRingTest`: Test hash ring

#### **2. Property-Based Tests** (Test with random data)
- Generate 100s of random inputs
- Verify properties hold for all inputs
- Example: "Put then Get always returns the same value"

#### **3. Integration Tests** (Test complete system)
- `DistributedCacheSystemIntegrationTest`: 16 comprehensive scenarios
- Tests multi-node clusters, failures, replication

#### **4. Performance Tests** (Test speed)
- `PerformanceTest`: Measures latency, throughput
- Verifies: GET < 10ms, replication < 100ms

---

## 🔧 CONFIGURATION FILES

### **pom.xml**
```xml
<!-- What it does: Maven project configuration -->

Defines:
- Project name and version
- Java version (17)
- Dependencies:
  - JUnit 5: For testing
  - jqwik: For property-based testing
  - SLF4J + Logback: For logging
- Build configuration
- Plugins
```

**Real-world analogy:** Like a shopping list for your project - tells Maven what libraries to download and how to build.

### **.gitignore**
```
# What it does: Tells Git which files to ignore

Ignores:
- target/: Compiled code (recreate with mvn compile)
- .idea/: IDE settings
- *.class: Compiled Java files
- .project-docs/: Documentation work-in-progress
```

---

## 🎬 HOW IT ALL WORKS TOGETHER

### **Example: Storing "user123" = "John Doe"**

```
Step 1: Client
--------------
client.put("user123", "John Doe")
   ↓

Step 2: Hash Ring
-----------------
hashRing.getPrimaryNode("user123")
  → Calculates hash: hash("user123") = 450
  → Finds nearest node: Node A (at position 500)
   ↓

Step 3: Network Client
----------------------
networkClient.send(NodeA, PutRequest("user123", "John Doe"))
   ↓

Step 4: Network Server (on Node A)
-----------------------------------
Receives PutRequest
Routes to handlePut()
   ↓

Step 5: Local Cache (on Node A)
--------------------------------
localCache.put("user123", "John Doe")
  → Wraps in CacheEntry
  → Stores in ConcurrentHashMap
  → Updates memory usage
  → Checks if need eviction
   ↓

Step 6: Replication
-------------------
replicationManager.replicatePut("user123", "John Doe")
  → Gets replica nodes: [Node B, Node C]
  → Sends ReplicateRequest to both
  → Waits up to 100ms
   ↓

Step 7: Metrics
---------------
metricsCollector.recordPut()
  → Increments operation count
  → Records latency
  → Updates memory usage
   ↓

Step 8: Response
----------------
Returns PutResponse("Success") to client
```

---

## 📊 REAL-WORLD USE CASES

### **1. E-commerce Website**
```
Problem: Product catalog has 1M products, database is slow
Solution: Cache product details in distributed cache
- Product ID → Product info
- Fast lookup: < 10ms instead of 100ms database query
- Handle Black Friday traffic spikes
```

### **2. Social Media App**
```
Problem: User profiles accessed millions of times per second
Solution: Cache user profiles
- User ID → Profile data
- Automatic replication prevents data loss
- Eviction removes inactive users
```

### **3. Gaming Server**
```
Problem: Player inventory needs instant access
Solution: Cache player data
- Player ID → Inventory, stats, achievements
- Distributed across multiple servers
- Failover ensures no data loss when server crashes
```

---

## 🎓 KEY CONCEPTS EXPLAINED

### **1. Distributed System**
Multiple computers working together as one system.

**Benefits:**
- Fault tolerance: If one computer dies, others continue
- Scalability: Add more computers to handle more load
- Performance: Split work across many computers

### **2. Consistent Hashing**
Algorithm to distribute data evenly across nodes.

**Why not simple hashing?**
- Simple: `node = hash(key) % numNodes`
- Problem: When nodes change, ALL data moves
- Consistent: Only affected keys move (minimal disruption)

### **3. Replication**
Storing multiple copies of data on different nodes.

**Why?**
- If primary node dies, data still available on replicas
- Can read from any replica (faster)
- Trade-off: Uses more storage space

### **4. Eviction**
Removing old data to make space for new data.

**Strategies:**
- LRU: Remove least recently used (good for time-based patterns)
- LFU: Remove least frequently used (good for popularity-based)
- FIFO: Remove oldest (simple but not optimal)

### **5. Thread Safety**
Multiple threads accessing data simultaneously without conflicts.

**Tools:**
- ConcurrentHashMap: Thread-safe HashMap
- AtomicLong: Thread-safe counter
- ReadWriteLock: Multiple readers, single writer
- Synchronized: One thread at a time

### **6. Non-Blocking I/O (NIO)**
Handle many network connections without creating threads for each.

**Traditional:**
- 1 connection = 1 thread
- 10,000 connections = 10,000 threads (expensive!)

**NIO:**
- 1 thread handles many connections
- 10,000 connections = ~10 threads (efficient!)

---

## 🚀 NEXT STEPS FOR LEARNING

### **Level 1: Understand the Flow**
1. Read `QuickStartDemo.java`
2. Run the demo
3. Follow a single operation (put/get) through the code

### **Level 2: Modify Simple Things**
1. Change cache capacity
2. Change replication factor
3. Add a new eviction policy
4. Add more metrics

### **Level 3: Add Features**
1. Add TTL (Time To Live) for entries
2. Add authentication
3. Add compression
4. Add encryption

### **Level 4: Optimize**
1. Improve serialization speed
2. Reduce memory usage
3. Optimize network protocol
4. Add batching

---

## 📚 RECOMMENDED READING ORDER

### **Day 1: Core Concepts**
1. `CacheEntry.java` - Understand data structure
2. `LocalCacheImpl.java` - Understand local storage
3. `LRUEvictionPolicy.java` - Understand eviction

### **Day 2: Distribution**
4. `ConsistentHashRing.java` - Understand data distribution
5. `NodeInfo.java` - Understand node representation
6. `CacheNodeImpl.java` - Understand a complete node

### **Day 3: Communication**
7. `Message.java` and message types - Understand communication
8. `NetworkServerImpl.java` - Understand receiving messages
9. `NetworkClientImpl.java` - Understand sending messages

### **Day 4: Reliability**
10. `ReplicationManagerImpl.java` - Understand backups
11. `HealthMonitorImpl.java` - Understand failure detection
12. `CacheClientImpl.java` - Understand client failover

### **Day 5: Complete Picture**
13. `QuickStartDemo.java` - See it all work together
14. Read tests - Understand expected behavior
15. Run the demo - See it in action!

---

## 💡 COMMON QUESTIONS

### **Q: Why Java?**
A: Java is great for distributed systems:
- Strong concurrency support (threads, locks)
- Good networking libraries
- Cross-platform (runs on any OS)
- Used by major companies (Google, Amazon, Netflix)

### **Q: Why 150 virtual nodes?**
A: Research shows 100-150 virtual nodes provides good balance:
- Fewer: Uneven distribution
- More: More memory, slower operations
- 150: Sweet spot for most use cases

### **Q: Why replication factor 3?**
A: Common in industry:
- 1: No backup (risky)
- 2: One backup (minimal safety)
- 3: Two backups (good balance)
- 5+: Very safe but expensive

### **Q: Why evict at 95% capacity?**
A: Prevents sudden full cache:
- 100%: Might fail during operations
- 95%: Buffer for incoming requests
- < 90%: Wastes space

### **Q: Can I use this in production?**
A: Yes, but consider:
- Add persistent storage (currently in-memory only)
- Add more security (authentication, encryption)
- Add monitoring/alerting
- Load test for your use case
- Add backup/restore functionality

---

## 🎉 CONGRATULATIONS!

You now understand:
- ✅ What each file does
- ✅ How components work together
- ✅ Why design decisions were made
- ✅ How to extend the system
- ✅ Real-world use cases

**You have a production-quality distributed cache system!** 🚀

Keep learning, keep building, and good luck! 💪
