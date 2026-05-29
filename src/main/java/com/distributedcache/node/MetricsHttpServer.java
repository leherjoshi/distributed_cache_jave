package com.distributedcache.node;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Simple HTTP server for exposing cache metrics.
 * Provides a /metrics endpoint that returns JSON representation of cache metrics.
 * Requirement 12.5: Expose metrics via HTTP endpoint
 */
public class MetricsHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(MetricsHttpServer.class);
    
    private final HttpServer server;
    private final MetricsCollector metricsCollector;
    private final int port;
    
    /**
     * Creates a new MetricsHttpServer.
     *
     * @param port the port to listen on
     * @param metricsCollector the metrics collector to expose
     * @throws IOException if the server cannot be created
     */
    public MetricsHttpServer(int port, MetricsCollector metricsCollector) throws IOException {
        if (metricsCollector == null) {
            throw new IllegalArgumentException("MetricsCollector cannot be null");
        }
        
        this.port = port;
        this.metricsCollector = metricsCollector;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Register /metrics endpoint
        server.createContext("/metrics", new MetricsHandler());
        
        logger.info("MetricsHttpServer created on port {}", port);
    }
    
    /**
     * Starts the HTTP server.
     */
    public void start() {
        server.setExecutor(null); // Use default executor
        server.start();
        logger.info("MetricsHttpServer started on port {}", port);
    }
    
    /**
     * Stops the HTTP server.
     */
    public void stop() {
        server.stop(0);
        logger.info("MetricsHttpServer stopped");
    }
    
    /**
     * Gets the port the server is listening on.
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Handler for /metrics endpoint.
     */
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }
            
            try {
                // Get current metrics
                CacheMetrics metrics = metricsCollector.getMetrics();
                
                // Build JSON response
                String json = buildMetricsJson(metrics);
                
                // Send response
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, json);
                
                logger.debug("Metrics endpoint accessed");
                
            } catch (Exception e) {
                logger.error("Error handling metrics request", e);
                sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
            }
        }
        
        /**
         * Builds JSON representation of cache metrics.
         * Requirement 12.5: Include hits per second, misses per second, average latency, memory usage percentage
         */
        private String buildMetricsJson(CacheMetrics metrics) {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"hitsPerSecond\": ").append(metrics.getHitsPerSecond()).append(",\n");
            json.append("  \"missesPerSecond\": ").append(metrics.getMissesPerSecond()).append(",\n");
            json.append("  \"averageGetLatencyMs\": ").append(metrics.getAverageGetLatencyMs()).append(",\n");
            json.append("  \"memoryUsagePercentage\": ").append(metrics.getMemoryUsagePercentage()).append(",\n");
            json.append("  \"totalHits\": ").append(metrics.getTotalHits()).append(",\n");
            json.append("  \"totalMisses\": ").append(metrics.getTotalMisses()).append("\n");
            json.append("}");
            return json.toString();
        }
        
        /**
         * Sends an HTTP response.
         */
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
