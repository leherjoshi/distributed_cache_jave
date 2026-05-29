package com.distributedcache.monitoring;

import com.distributedcache.node.CacheMetrics;
import com.distributedcache.node.MetricsCollector;
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
 * HTTP server that exposes cache metrics at /metrics endpoint.
 * Requirement 12.5: Expose metrics via HTTP endpoint
 */
public class MetricsServer {
    private static final Logger logger = LoggerFactory.getLogger(MetricsServer.class);
    
    private final MetricsCollector metricsCollector;
    private final int port;
    private HttpServer server;
    
    /**
     * Creates a new MetricsServer.
     *
     * @param metricsCollector the metrics collector to expose
     * @param port the port to listen on
     */
    public MetricsServer(MetricsCollector metricsCollector, int port) {
        if (metricsCollector == null) {
            throw new IllegalArgumentException("MetricsCollector cannot be null");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        
        this.metricsCollector = metricsCollector;
        this.port = port;
    }
    
    /**
     * Starts the metrics HTTP server.
     *
     * @throws IOException if the server cannot be started
     */
    public void start() throws IOException {
        if (server != null) {
            logger.warn("MetricsServer is already running");
            return;
        }
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", new MetricsHandler());
        server.setExecutor(null); // Use default executor
        server.start();
        
        logger.info("MetricsServer started on port {}", port);
    }
    
    /**
     * Stops the metrics HTTP server.
     */
    public void stop() {
        if (server == null) {
            logger.warn("MetricsServer is not running");
            return;
        }
        
        server.stop(0);
        server = null;
        
        logger.info("MetricsServer stopped");
    }
    
    /**
     * Gets the port the server is listening on.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Checks if the server is running.
     *
     * @return true if the server is running
     */
    public boolean isRunning() {
        return server != null;
    }
    
    /**
     * HTTP handler for the /metrics endpoint.
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
                
                // Convert to JSON
                String json = metricsToJson(metrics);
                
                // Send response
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                sendResponse(exchange, 200, json);
                
                logger.debug("Served metrics: {}", json);
                
            } catch (Exception e) {
                logger.error("Error serving metrics", e);
                sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
            }
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
        
        /**
         * Converts CacheMetrics to JSON format.
         * Requirement 12.5: Return JSON representation of metrics
         */
        private String metricsToJson(CacheMetrics metrics) {
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
    }
}
