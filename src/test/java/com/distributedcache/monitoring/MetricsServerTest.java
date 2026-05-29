package com.distributedcache.monitoring;

import com.distributedcache.node.CacheMetrics;
import com.distributedcache.node.MetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MetricsServer.
 * Requirement 12.5: Test HTTP endpoint for metrics exposure
 */
class MetricsServerTest {
    
    private MetricsCollector mockMetricsCollector;
    private MetricsServer metricsServer;
    private static final int TEST_PORT = 18080;
    
    @BeforeEach
    void setUp() {
        mockMetricsCollector = mock(MetricsCollector.class);
    }
    
    @AfterEach
    void tearDown() {
        if (metricsServer != null && metricsServer.isRunning()) {
            metricsServer.stop();
        }
    }
    
    @Test
    @DisplayName("Constructor should throw exception for null MetricsCollector")
    void testConstructorNullMetricsCollector() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MetricsServer(null, TEST_PORT);
        });
    }
    
    @Test
    @DisplayName("Constructor should throw exception for invalid port (too low)")
    void testConstructorInvalidPortTooLow() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MetricsServer(mockMetricsCollector, 0);
        });
    }
    
    @Test
    @DisplayName("Constructor should throw exception for invalid port (too high)")
    void testConstructorInvalidPortTooHigh() {
        assertThrows(IllegalArgumentException.class, () -> {
            new MetricsServer(mockMetricsCollector, 65536);
        });
    }
    
    @Test
    @DisplayName("Constructor should accept valid port")
    void testConstructorValidPort() {
        assertDoesNotThrow(() -> {
            metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        });
    }
    
    @Test
    @DisplayName("getPort should return configured port")
    void testGetPort() {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        assertEquals(TEST_PORT, metricsServer.getPort());
    }
    
    @Test
    @DisplayName("isRunning should return false before start")
    void testIsRunningBeforeStart() {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        assertFalse(metricsServer.isRunning());
    }
    
    @Test
    @DisplayName("start should start the HTTP server")
    void testStart() throws IOException {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        assertTrue(metricsServer.isRunning());
    }
    
    @Test
    @DisplayName("stop should stop the HTTP server")
    void testStop() throws IOException {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        assertTrue(metricsServer.isRunning());
        
        metricsServer.stop();
        assertFalse(metricsServer.isRunning());
    }
    
    @Test
    @DisplayName("start should be idempotent (calling twice should not fail)")
    void testStartIdempotent() throws IOException {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        metricsServer.start(); // Should not throw exception
        
        assertTrue(metricsServer.isRunning());
    }
    
    @Test
    @DisplayName("stop should be idempotent (calling twice should not fail)")
    void testStopIdempotent() throws IOException {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        metricsServer.stop();
        metricsServer.stop(); // Should not throw exception
        
        assertFalse(metricsServer.isRunning());
    }
    
    @Test
    @DisplayName("/metrics endpoint should return 200 OK for GET request")
    void testMetricsEndpointGetRequest() throws IOException {
        // Setup mock metrics
        CacheMetrics mockMetrics = new CacheMetrics(10.5, 2.3, 5.2, 45.6, 100, 20);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(200, conn.getResponseCode());
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should return JSON content type")
    void testMetricsEndpointContentType() throws IOException {
        // Setup mock metrics
        CacheMetrics mockMetrics = new CacheMetrics(10.5, 2.3, 5.2, 45.6, 100, 20);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        String contentType = conn.getHeaderField("Content-Type");
        assertEquals("application/json", contentType);
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should return correct JSON format")
    void testMetricsEndpointJsonFormat() throws IOException {
        // Setup mock metrics
        CacheMetrics mockMetrics = new CacheMetrics(10.5, 2.3, 5.2, 45.6, 100, 20);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // Read response
        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        // Verify JSON contains expected fields
        assertTrue(response.contains("\"hitsPerSecond\": 10.5"));
        assertTrue(response.contains("\"missesPerSecond\": 2.3"));
        assertTrue(response.contains("\"averageGetLatencyMs\": 5.2"));
        assertTrue(response.contains("\"memoryUsagePercentage\": 45.6"));
        assertTrue(response.contains("\"totalHits\": 100"));
        assertTrue(response.contains("\"totalMisses\": 20"));
        
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should return 405 for POST request")
    void testMetricsEndpointPostRequest() throws IOException {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP POST request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        
        assertEquals(405, conn.getResponseCode());
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should return 405 for PUT request")
    void testMetricsEndpointPutRequest() throws IOException {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP PUT request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        
        assertEquals(405, conn.getResponseCode());
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should return 405 for DELETE request")
    void testMetricsEndpointDeleteRequest() throws IOException {
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP DELETE request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        
        assertEquals(405, conn.getResponseCode());
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should handle zero values correctly")
    void testMetricsEndpointZeroValues() throws IOException {
        // Setup mock metrics with zero values
        CacheMetrics mockMetrics = new CacheMetrics(0.0, 0.0, 0.0, 0.0, 0, 0);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // Read response
        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        // Verify JSON contains zero values
        assertTrue(response.contains("\"hitsPerSecond\": 0.0"));
        assertTrue(response.contains("\"missesPerSecond\": 0.0"));
        assertTrue(response.contains("\"averageGetLatencyMs\": 0.0"));
        assertTrue(response.contains("\"memoryUsagePercentage\": 0.0"));
        assertTrue(response.contains("\"totalHits\": 0"));
        assertTrue(response.contains("\"totalMisses\": 0"));
        
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should handle large values correctly")
    void testMetricsEndpointLargeValues() throws IOException {
        // Setup mock metrics with large values
        CacheMetrics mockMetrics = new CacheMetrics(1000.5, 500.3, 100.2, 99.9, 1000000, 500000);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // Read response
        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        // Verify JSON contains large values
        assertTrue(response.contains("\"hitsPerSecond\": 1000.5"));
        assertTrue(response.contains("\"missesPerSecond\": 500.3"));
        assertTrue(response.contains("\"averageGetLatencyMs\": 100.2"));
        assertTrue(response.contains("\"memoryUsagePercentage\": 99.9"));
        assertTrue(response.contains("\"totalHits\": 1000000"));
        assertTrue(response.contains("\"totalMisses\": 500000"));
        
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should call getMetrics on collector")
    void testMetricsEndpointCallsCollector() throws IOException {
        // Setup mock metrics
        CacheMetrics mockMetrics = new CacheMetrics(10.5, 2.3, 5.2, 45.6, 100, 20);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.getResponseCode(); // Trigger the request
        
        // Verify getMetrics was called
        verify(mockMetricsCollector, atLeastOnce()).getMetrics();
        
        conn.disconnect();
    }
    
    @Test
    @DisplayName("/metrics endpoint should handle multiple concurrent requests")
    void testMetricsEndpointConcurrentRequests() throws IOException, InterruptedException {
        // Setup mock metrics
        CacheMetrics mockMetrics = new CacheMetrics(10.5, 2.3, 5.2, 45.6, 100, 20);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make multiple concurrent requests
        int numRequests = 10;
        Thread[] threads = new Thread[numRequests];
        
        for (int i = 0; i < numRequests; i++) {
            threads[i] = new Thread(() -> {
                try {
                    URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    assertEquals(200, conn.getResponseCode());
                    conn.disconnect();
                } catch (IOException e) {
                    fail("Request failed: " + e.getMessage());
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify getMetrics was called multiple times
        verify(mockMetricsCollector, atLeast(numRequests)).getMetrics();
    }
    
    @Test
    @DisplayName("Server should handle exception from metrics collector gracefully")
    void testMetricsCollectorException() throws IOException {
        // Setup mock to throw exception
        when(mockMetricsCollector.getMetrics()).thenThrow(new RuntimeException("Test exception"));
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // Should return 500 Internal Server Error
        assertEquals(500, conn.getResponseCode());
        
        conn.disconnect();
    }
    
    @Test
    @DisplayName("Server should be accessible after restart")
    void testServerRestart() throws IOException {
        // Setup mock metrics
        CacheMetrics mockMetrics = new CacheMetrics(10.5, 2.3, 5.2, 45.6, 100, 20);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        
        // Start, stop, and start again
        metricsServer.start();
        assertTrue(metricsServer.isRunning());
        
        metricsServer.stop();
        assertFalse(metricsServer.isRunning());
        
        metricsServer.start();
        assertTrue(metricsServer.isRunning());
        
        // Make HTTP request to verify it's working
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        assertEquals(200, conn.getResponseCode());
        conn.disconnect();
    }
    
    @Test
    @DisplayName("JSON response should be valid and parseable")
    void testJsonResponseValidity() throws IOException {
        // Setup mock metrics
        CacheMetrics mockMetrics = new CacheMetrics(10.5, 2.3, 5.2, 45.6, 100, 20);
        when(mockMetricsCollector.getMetrics()).thenReturn(mockMetrics);
        
        metricsServer = new MetricsServer(mockMetricsCollector, TEST_PORT);
        metricsServer.start();
        
        // Make HTTP request
        URL url = new URL("http://localhost:" + TEST_PORT + "/metrics");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        
        // Read response
        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        
        // Verify JSON structure (basic validation)
        assertTrue(response.startsWith("{"));
        assertTrue(response.endsWith("}"));
        assertTrue(response.contains("\"hitsPerSecond\":"));
        assertTrue(response.contains("\"missesPerSecond\":"));
        assertTrue(response.contains("\"averageGetLatencyMs\":"));
        assertTrue(response.contains("\"memoryUsagePercentage\":"));
        assertTrue(response.contains("\"totalHits\":"));
        assertTrue(response.contains("\"totalMisses\":"));
        
        conn.disconnect();
    }
}
