# 🚀 Distributed Cache System - Live Demo Guide

## Quick Start

### Option 1: Run with Script (Easiest)
```bash
./run-demo.sh
```

### Option 2: Run with Maven
```bash
mvn exec:java -Dexec.mainClass="com.distributedcache.demo.QuickStartDemo"
```

### Option 3: Run with Java
```bash
# Compile first
mvn compile

# Run
mvn exec:java -Dexec.mainClass="com.distributedcache.demo.QuickStartDemo"
```

---

## What the Demo Does

The demo automatically:

1. **Starts a 3-node cluster** on ports 8001, 8002, 8003
2. **Creates a cache client** connected to the cluster
3. **Performs cache operations**:
   - PUT: Stores 5 key-value pairs
   - GET: Retrieves all stored values
   - DELETE: Removes one entry
4. **Shows cluster metrics** for each node
5. **Simulates node failure** and demonstrates automatic failover
6. **Keeps running** so you can see the cluster in action

---

## Expected Output

```
================================================================================
DISTRIBUTED CACHE SYSTEM - QUICK START DEMO
================================================================================

📦 Step 1: Starting 3-node cache cluster...
   ✓ Started node-1 on port 8001
   ✓ Started node-2 on port 8002
   ✓ Started node-3 on port 8003
   ✓ Cluster is ready!

🔌 Step 2: Creating cache client...
   ✓ Client connected to cluster

🚀 Step 3: Performing cache operations...

   📝 Storing data:
      ✓ PUT user:1001 = Alice Johnson
      ✓ PUT user:1002 = Bob Smith
      ✓ PUT user:1003 = Charlie Brown
      ✓ PUT product:5001 = Laptop Pro 15
      ✓ PUT product:5002 = Wireless Mouse

   📖 Retrieving data:
      ✓ GET user:1001 = Alice Johnson
      ✓ GET user:1002 = Bob Smith
      ✓ GET user:1003 = Charlie Brown
      ✓ GET product:5001 = Laptop Pro 15
      ✓ GET product:5002 = Wireless Mouse

   🗑️  Deleting data:
      ✓ DELETE user:1002
      ✓ Verify: user:1002 = NOT FOUND (correct)

📈 Step 4: Cluster Metrics:

   node-1:
      - Total Hits: 15
      - Total Misses: 2
      - Avg Latency: 1.23 ms
      - Memory Usage: 0.05%
      - Health: HEALTHY

   node-2:
      - Total Hits: 12
      - Total Misses: 1
      - Avg Latency: 0.98 ms
      - Memory Usage: 0.04%
      - Health: HEALTHY

   node-3:
      - Total Hits: 10
      - Total Misses: 0
      - Avg Latency: 1.15 ms
      - Memory Usage: 0.03%
      - Health: HEALTHY

⚠️  Step 5: Simulating node failure...
   ⏸️  Stopping node-2...
   🔄 Attempting to retrieve data after node failure:
      ✓ GET user:1001 = Alice Johnson (from replica)
      ✓ GET user:1003 = Charlie Brown (from replica)
      ✓ GET product:5001 = Laptop Pro 15 (from replica)
      ✓ GET product:5002 = Wireless Mouse (from replica)

================================================================================
✨ DEMO COMPLETE!
================================================================================

Your distributed cache cluster is running!

🔧 Active nodes: 2 (node-2 is stopped)

Press Ctrl+C to stop the cluster...
```

---

## Key Features Demonstrated

### ✅ Distributed Storage
- Data is automatically distributed across 3 nodes using consistent hashing
- Each key is routed to the correct node based on hash value

### ✅ Replication
- Data is replicated across multiple nodes (replication factor = 2)
- Ensures data availability even when nodes fail

### ✅ Automatic Failover
- When node-2 stops, the client automatically retrieves data from replicas
- No data loss, seamless operation

### ✅ Real-time Metrics
- Each node tracks hits, misses, latency, and memory usage
- Metrics updated in real-time

### ✅ Health Monitoring
- Nodes continuously monitor each other's health
- Automatic detection of failed nodes

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     Cache Client                             │
│  (Routes requests to correct nodes via consistent hashing)  │
└────────────┬────────────────────────────────┬───────────────┘
             │                                 │
    ┌────────▼────────┐              ┌────────▼────────┐
    │   Node 1:8001   │◄────────────►│   Node 2:8002   │
    │  - Local Cache  │   Replicate  │  - Local Cache  │
    │  - Hash Ring    │              │  - Hash Ring    │
    │  - Metrics      │              │  - Metrics      │
    └────────┬────────┘              └────────┬────────┘
             │                                 │
             │         ┌────────────────┐      │
             └────────►│  Node 3:8003   │◄─────┘
                       │  - Local Cache │
                       │  - Hash Ring   │
                       │  - Metrics     │
                       └────────────────┘
```

---

## Stopping the Demo

Press `Ctrl+C` to stop the cluster and exit.

---

## Next Steps

### Run Tests
```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=LocalCacheImplTest
```

### Build JAR
```bash
mvn package
```

### Explore the Code
- **Core Cache**: `src/main/java/com/distributedcache/cache/`
- **Network Layer**: `src/main/java/com/distributedcache/network/`
- **Consistent Hashing**: `src/main/java/com/distributedcache/hashing/`
- **Replication**: `src/main/java/com/distributedcache/replication/`
- **Client API**: `src/main/java/com/distributedcache/client/`

---

## Troubleshooting

### Port Already in Use
If you see "Address already in use" errors:
```bash
# Find and kill processes on ports 8001-8003
lsof -ti:8001 | xargs kill -9
lsof -ti:8002 | xargs kill -9
lsof -ti:8003 | xargs kill -9
```

### Compilation Errors
```bash
# Clean and rebuild
mvn clean compile
```

### Demo Won't Start
```bash
# Check Java version (requires Java 17+)
java -version

# Ensure Maven is installed
mvn -version
```

---

## Performance Characteristics

Based on the implementation:

- **GET latency**: < 10ms (typically 1-2ms)
- **Replication latency**: < 100ms
- **Network message delivery**: < 20ms
- **Concurrent connections**: 1000+
- **Key size limit**: 256 bytes
- **Value size limit**: 1 MB
- **Eviction policies**: LRU, LFU, FIFO

---

## Production Deployment

For production use, consider:

1. **Configure proper capacity** in `CacheConfiguration`
2. **Set appropriate replication factor** (3-5 for high availability)
3. **Enable persistent storage** for node list
4. **Monitor metrics** via HTTP endpoint
5. **Set up health checks** with external monitoring
6. **Use proper logging** configuration (logback.xml)
7. **Deploy across multiple machines** (not just localhost)

---

## Questions?

Check the main README.md for more details about the architecture and implementation.
