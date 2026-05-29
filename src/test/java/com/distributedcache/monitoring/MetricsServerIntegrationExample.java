package com.distributedcache.monitoring;

import com.distributedcache.node.MetricsCollector;
import com.distributedcache.node.MetricsCollectorImpl;
import com.distributedcache.node.CacheMetrics;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Integration example demonstrating MetricsServer with real MetricsCollector.
 * This is not a test, but an example showing how to use the components together.
 */
public class MetricsServerIntegrationExample {
    
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("=== MetricsServer Integration Example ===\n");
        
        // Create a real metrics collector
        MetricsCollector collector = new MetricsCollectorImpl();
        
        // Simulate some cache activity
        System.out.println("Simulating cache activity...");
        for (int i = 0; i < 10; i++) {
            collector.recordHit();
        }
        for (int i = 0; i < 3; i++) {
            collector.recordMiss();
        }
        collector.recordGetLatency(5);
        collector.recordGetLatency(10);
        collector.recordGetLatency(15);
        collector.recordMemoryUsage(450000L, 1000000L); // 45% usage
        
        // Wait a bit for metrics to update
        Thread.sleep(1100);
        
        // Create and start metrics server
        int port = 18888;
        MetricsServer server = new MetricsServer(collector, port);
        server.start();
        
        System.out.println("MetricsServer started on port " + port);
        System.out.println("Endpoint: http://localhost:" + port + "/metrics\n");
        
        // Query the metrics endpoint
        System.out.println("Querying metrics endpoint...");
        URL url = new URL("http://localhost:" + port + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        int responseCode = conn.getResponseCode();
        System.out.println("Response Code: " + responseCode);
        System.out.println("Content-Type: " + conn.getHeaderField("Content-Type"));
        System.out.println("\nMetrics JSON Response:");
        System.out.println("----------------------");
        
        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.println(response);
        
        conn.disconnect();
        
        // Also show metrics directly from collector
        System.out.println("\n\nDirect Metrics from Collector:");
        System.out.println("------------------------------");
        CacheMetrics metrics = collector.getMetrics();
        System.out.println("Hits per second: " + metrics.getHitsPerSecond());
        System.out.println("Misses per second: " + metrics.getMissesPerSecond());
        System.out.println("Average latency: " + metrics.getAverageGetLatencyMs() + " ms");
        System.out.println("Memory usage: " + metrics.getMemoryUsagePercentage() + "%");
        System.out.println("Total hits: " + metrics.getTotalHits());
        System.out.println("Total misses: " + metrics.getTotalMisses());
        
        // Stop the server
        System.out.println("\n\nStopping MetricsServer...");
        server.stop();
        System.out.println("MetricsServer stopped.");
        
        System.out.println("\n=== Example Complete ===");
    }
}
