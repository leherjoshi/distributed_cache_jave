package com.distributedcache.node;

import com.distributedcache.hashing.NodeInfo;
import com.distributedcache.network.HealthCheck;
import com.distributedcache.network.HealthResponse;
import com.distributedcache.network.NetworkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Implementation of HealthMonitor that performs scheduled health checks on cache nodes.
 * 
 * This implementation:
 * - Uses ScheduledExecutorService for periodic health checks every 5 seconds
 * - Tracks consecutive failure count per node
 * - Marks nodes unavailable after 3 consecutive failures
 * - Marks nodes healthy when they respond after being unavailable
 * - Logs all status changes with timestamps
 */
public class HealthMonitorImpl implements HealthMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthMonitorImpl.class);
    
    private final NetworkClient networkClient;
    private final String sourceNodeId;
    private final Set<NodeInfo> nodesToMonitor;
    private final Duration checkInterval;
    
    // Thread-safe tracking of node health
    private final ConcurrentHashMap<String, HealthStatus> nodeStatusMap;
    private final ConcurrentHashMap<String, Integer> consecutiveFailures;
    private final ConcurrentHashMap<String, Instant> lastCheckTime;
    
    // Callbacks for status changes
    private final List<Consumer<NodeStatusEvent>> statusChangeCallbacks;
    
    // Scheduled executor for periodic health checks
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> healthCheckTask;
    private volatile boolean running;
    
    /**
     * Creates a new HealthMonitorImpl with default check interval.
     *
     * @param networkClient the network client for sending health checks
     * @param sourceNodeId the ID of this node
     * @param nodesToMonitor the set of nodes to monitor
     */
    public HealthMonitorImpl(NetworkClient networkClient, String sourceNodeId, Set<NodeInfo> nodesToMonitor) {
        this(networkClient, sourceNodeId, nodesToMonitor, DEFAULT_CHECK_INTERVAL);
    }
    
    /**
     * Creates a new HealthMonitorImpl with custom check interval.
     *
     * @param networkClient the network client for sending health checks
     * @param sourceNodeId the ID of this node
     * @param nodesToMonitor the set of nodes to monitor
     * @param checkInterval the interval between health checks
     */
    public HealthMonitorImpl(NetworkClient networkClient, String sourceNodeId, 
                            Set<NodeInfo> nodesToMonitor, Duration checkInterval) {
        this.networkClient = Objects.requireNonNull(networkClient, "networkClient cannot be null");
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId cannot be null");
        this.nodesToMonitor = ConcurrentHashMap.newKeySet();
        if (nodesToMonitor != null) {
            this.nodesToMonitor.addAll(nodesToMonitor);
        }
        this.checkInterval = Objects.requireNonNull(checkInterval, "checkInterval cannot be null");
        
        this.nodeStatusMap = new ConcurrentHashMap<>();
        this.consecutiveFailures = new ConcurrentHashMap<>();
        this.lastCheckTime = new ConcurrentHashMap<>();
        this.statusChangeCallbacks = new CopyOnWriteArrayList<>();
        this.running = false;
        
        // Initialize all nodes as HEALTHY
        for (NodeInfo node : this.nodesToMonitor) {
            nodeStatusMap.put(node.getNodeId(), HealthStatus.HEALTHY);
            consecutiveFailures.put(node.getNodeId(), 0);
        }
    }
    
    @Override
    public void start() {
        if (running) {
            logger.warn("HealthMonitor is already running");
            return;
        }
        
        logger.info("Starting HealthMonitor with check interval: {}", checkInterval);
        
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "health-monitor");
            t.setDaemon(true);
            return t;
        });
        
        running = true;
        
        // Schedule periodic health checks
        healthCheckTask = scheduler.scheduleAtFixedRate(
            this::performHealthChecks,
            0, // Initial delay
            checkInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
        
        logger.info("HealthMonitor started successfully");
    }
    
    @Override
    public void stop() {
        if (!running) {
            logger.warn("HealthMonitor is not running");
            return;
        }
        
        logger.info("Stopping HealthMonitor");
        
        running = false;
        
        if (healthCheckTask != null) {
            healthCheckTask.cancel(false);
        }
        
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("HealthMonitor stopped successfully");
    }
    
    @Override
    public void onStatusChange(Consumer<NodeStatusEvent> callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        statusChangeCallbacks.add(callback);
        logger.debug("Registered status change callback");
    }
    
    @Override
    public HealthStatus getNodeStatus(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        return nodeStatusMap.getOrDefault(nodeId, HealthStatus.UNAVAILABLE);
    }
    
    @Override
    public Set<NodeInfo> getHealthyNodes() {
        return nodesToMonitor.stream()
            .filter(node -> getNodeStatus(node.getNodeId()) == HealthStatus.HEALTHY)
            .collect(Collectors.toSet());
    }
    
    @Override
    public void checkNode(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        
        NodeInfo node = nodesToMonitor.stream()
            .filter(n -> n.getNodeId().equals(nodeId))
            .findFirst()
            .orElse(null);
        
        if (node == null) {
            logger.warn("Cannot check node {}: not in monitored nodes list", nodeId);
            return;
        }
        
        logger.debug("Manually checking node: {}", nodeId);
        performHealthCheck(node);
    }
    
    /**
     * Adds a node to the monitoring list.
     *
     * @param node the node to add
     */
    public void addNode(NodeInfo node) {
        Objects.requireNonNull(node, "node cannot be null");
        
        if (nodesToMonitor.add(node)) {
            nodeStatusMap.put(node.getNodeId(), HealthStatus.HEALTHY);
            consecutiveFailures.put(node.getNodeId(), 0);
            logger.info("Added node to monitoring: {}", node.getNodeId());
        }
    }
    
    /**
     * Removes a node from the monitoring list.
     *
     * @param nodeId the ID of the node to remove
     */
    public void removeNode(String nodeId) {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
        
        nodesToMonitor.removeIf(node -> node.getNodeId().equals(nodeId));
        nodeStatusMap.remove(nodeId);
        consecutiveFailures.remove(nodeId);
        lastCheckTime.remove(nodeId);
        
        logger.info("Removed node from monitoring: {}", nodeId);
    }
    
    /**
     * Performs health checks on all monitored nodes.
     */
    private void performHealthChecks() {
        if (!running) {
            return;
        }
        
        logger.debug("Performing health checks on {} nodes", nodesToMonitor.size());
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (NodeInfo node : nodesToMonitor) {
            // Skip checking ourselves
            if (node.getNodeId().equals(sourceNodeId)) {
                continue;
            }
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> performHealthCheck(node)
            );
            futures.add(future);
        }
        
        // Wait for all health checks to complete (with timeout)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(checkInterval.toMillis() - 100, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                logger.warn("Some health checks did not complete in time", ex);
                return null;
            })
            .join();
    }
    
    /**
     * Performs a health check on a single node.
     *
     * @param node the node to check
     */
    private void performHealthCheck(NodeInfo node) {
        String nodeId = node.getNodeId();
        Instant checkTime = Instant.now();
        lastCheckTime.put(nodeId, checkTime);
        
        try {
            HealthCheck healthCheck = new HealthCheck(sourceNodeId);
            
            CompletableFuture<HealthResponse> future = networkClient.send(node, healthCheck);
            
            // Wait for response with timeout
            HealthResponse response = future.get(2, TimeUnit.SECONDS);
            
            if (response != null) {
                handleHealthCheckSuccess(node, checkTime);
            } else {
                handleHealthCheckFailure(node, checkTime, "No response received");
            }
            
        } catch (TimeoutException e) {
            handleHealthCheckFailure(node, checkTime, "Health check timeout");
        } catch (Exception e) {
            handleHealthCheckFailure(node, checkTime, "Health check failed: " + e.getMessage());
        }
    }
    
    /**
     * Handles a successful health check response.
     *
     * @param node the node that responded
     * @param checkTime the time of the check
     */
    private void handleHealthCheckSuccess(NodeInfo node, Instant checkTime) {
        String nodeId = node.getNodeId();
        HealthStatus oldStatus = nodeStatusMap.get(nodeId);
        
        // Reset consecutive failures
        consecutiveFailures.put(nodeId, 0);
        
        // If node was previously unavailable, mark it as healthy
        if (oldStatus != HealthStatus.HEALTHY) {
            nodeStatusMap.put(nodeId, HealthStatus.HEALTHY);
            
            logger.info("[{}] Node {} status changed: {} -> HEALTHY", 
                checkTime, nodeId, oldStatus);
            
            // Notify callbacks
            NodeStatusEvent event = new NodeStatusEvent(node, oldStatus, HealthStatus.HEALTHY);
            notifyStatusChange(event);
        } else {
            logger.trace("Node {} is healthy", nodeId);
        }
    }
    
    /**
     * Handles a failed health check.
     *
     * @param node the node that failed
     * @param checkTime the time of the check
     * @param reason the reason for failure
     */
    private void handleHealthCheckFailure(NodeInfo node, Instant checkTime, String reason) {
        String nodeId = node.getNodeId();
        HealthStatus oldStatus = nodeStatusMap.get(nodeId);
        
        // Increment consecutive failures
        int failures = consecutiveFailures.compute(nodeId, (k, v) -> (v == null ? 0 : v) + 1);
        
        logger.debug("Node {} health check failed (attempt {}/{}): {}", 
            nodeId, failures, FAILURE_THRESHOLD, reason);
        
        // Mark as unavailable after threshold consecutive failures
        if (failures >= FAILURE_THRESHOLD && oldStatus != HealthStatus.UNAVAILABLE) {
            nodeStatusMap.put(nodeId, HealthStatus.UNAVAILABLE);
            
            logger.warn("[{}] Node {} status changed: {} -> UNAVAILABLE (after {} consecutive failures)", 
                checkTime, nodeId, oldStatus, failures);
            
            // Notify callbacks
            NodeStatusEvent event = new NodeStatusEvent(node, oldStatus, HealthStatus.UNAVAILABLE);
            notifyStatusChange(event);
        }
    }
    
    /**
     * Notifies all registered callbacks of a status change.
     *
     * @param event the status change event
     */
    private void notifyStatusChange(NodeStatusEvent event) {
        for (Consumer<NodeStatusEvent> callback : statusChangeCallbacks) {
            try {
                callback.accept(event);
            } catch (Exception e) {
                logger.error("Error in status change callback", e);
            }
        }
    }
    
    /**
     * Gets the number of consecutive failures for a node.
     * Useful for testing.
     *
     * @param nodeId the node ID
     * @return the number of consecutive failures
     */
    int getConsecutiveFailures(String nodeId) {
        return consecutiveFailures.getOrDefault(nodeId, 0);
    }
    
    /**
     * Gets the last check time for a node.
     * Useful for testing.
     *
     * @param nodeId the node ID
     * @return the last check time, or null if never checked
     */
    Instant getLastCheckTime(String nodeId) {
        return lastCheckTime.get(nodeId);
    }
}
