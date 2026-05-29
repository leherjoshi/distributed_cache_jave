package com.distributedcache.network;

import com.distributedcache.exceptions.NetworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of NetworkServer using Java NIO for non-blocking I/O.
 * Supports concurrent connections and message routing to registered handlers.
 */
public class NetworkServerImpl implements NetworkServer {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkServerImpl.class);
    
    private static final int BUFFER_SIZE = 8192;
    private static final int SELECTOR_TIMEOUT_MS = 1000;
    
    private final int port;
    private final Map<MessageType, MessageHandler> handlers;
    private final AtomicBoolean running;
    
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Thread acceptorThread;
    private ExecutorService workerPool;
    
    /**
     * Creates a new NetworkServerImpl.
     * 
     * @param port the port to bind to
     */
    public NetworkServerImpl(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.port = port;
        this.handlers = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
    }
    
    @Override
    public void start() throws NetworkException {
        if (running.get()) {
            throw new NetworkException("Server is already running");
        }
        
        try {
            // Create selector
            selector = Selector.open();
            
            // Create and configure server socket channel
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            
            // Register the server socket channel with selector for accept operations
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            // Create worker thread pool for handling messages
            workerPool = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setName("NetworkServer-Worker-" + t.getId());
                t.setDaemon(true);
                return t;
            });
            
            running.set(true);
            
            // Start acceptor thread
            acceptorThread = new Thread(this::runEventLoop, "NetworkServer-Acceptor");
            acceptorThread.setDaemon(true);
            acceptorThread.start();
            
            logger.info("NetworkServer started on port {}", port);
            
        } catch (IOException e) {
            throw new NetworkException("Failed to start server on port " + port, e);
        }
    }
    
    @Override
    public void stop() throws NetworkException {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        try {
            // Close selector to wake up the select() call
            if (selector != null && selector.isOpen()) {
                selector.wakeup();
            }
            
            // Wait for acceptor thread to finish
            if (acceptorThread != null) {
                acceptorThread.join(5000);
            }
            
            // Shutdown worker pool
            if (workerPool != null) {
                workerPool.shutdown();
                if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            }
            
            // Close server channel
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
            
            // Close selector
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            
            logger.info("NetworkServer stopped");
            
        } catch (IOException | InterruptedException e) {
            throw new NetworkException("Failed to stop server", e);
        }
    }
    
    @Override
    public void registerHandler(MessageType type, MessageHandler handler) {
        if (type == null) {
            throw new IllegalArgumentException("MessageType cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("MessageHandler cannot be null");
        }
        handlers.put(type, handler);
        logger.debug("Registered handler for message type: {}", type);
    }
    
    @Override
    public int getPort() {
        return port;
    }
    
    @Override
    public void close() throws Exception {
        stop();
    }
    
    /**
     * Main event loop that handles accept and read operations.
     */
    private void runEventLoop() {
        logger.debug("Event loop started");
        
        while (running.get()) {
            try {
                // Wait for events
                int readyChannels = selector.select(SELECTOR_TIMEOUT_MS);
                
                if (readyChannels == 0) {
                    continue;
                }
                
                // Process selected keys
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                    } catch (Exception e) {
                        logger.error("Error processing key", e);
                        closeChannel(key);
                    }
                }
                
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("Error in event loop", e);
                }
            }
        }
        
        logger.debug("Event loop stopped");
    }
    
    /**
     * Handles new incoming connections.
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
            logger.debug("Accepted connection from {}", clientChannel.getRemoteAddress());
        }
    }
    
    /**
     * Handles reading data from a client connection.
     */
    private void handleRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        
        // Submit to worker pool for processing
        workerPool.submit(() -> processMessage(channel, key));
    }
    
    /**
     * Processes an incoming message from a client.
     */
    private void processMessage(SocketChannel channel, SelectionKey key) {
        try {
            // Read message length (4 bytes)
            ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
            int bytesRead = readFully(channel, lengthBuffer);
            
            if (bytesRead == -1) {
                // Connection closed
                closeChannel(key);
                return;
            }
            
            lengthBuffer.flip();
            int messageLength = lengthBuffer.getInt();
            
            if (messageLength <= 0 || messageLength > 10 * 1024 * 1024) {
                logger.warn("Invalid message length: {}", messageLength);
                closeChannel(key);
                return;
            }
            
            // Read message data
            ByteBuffer messageBuffer = ByteBuffer.allocate(messageLength);
            bytesRead = readFully(channel, messageBuffer);
            
            if (bytesRead == -1) {
                closeChannel(key);
                return;
            }
            
            messageBuffer.flip();
            byte[] messageBytes = new byte[messageBuffer.remaining()];
            messageBuffer.get(messageBytes);
            
            // Deserialize message
            Message message = deserializeMessage(messageBytes);
            
            if (message == null) {
                logger.warn("Failed to deserialize message");
                closeChannel(key);
                return;
            }
            
            logger.debug("Received message: type={}, id={}", message.getType(), message.getMessageId());
            
            // Route to handler
            MessageHandler handler = handlers.get(message.getType());
            
            if (handler == null) {
                logger.warn("No handler registered for message type: {}", message.getType());
                closeChannel(key);
                return;
            }
            
            // Handle message and get response
            Message response = handler.handle(message);
            
            // Send response if present
            if (response != null) {
                sendResponse(channel, response);
            }
            
            // Close connection after processing
            closeChannel(key);
            
        } catch (Exception e) {
            logger.error("Error processing message", e);
            closeChannel(key);
        }
    }
    
    /**
     * Reads data from channel until buffer is full or connection is closed.
     * 
     * @return number of bytes read, or -1 if connection closed
     */
    private int readFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
        int totalBytesRead = 0;
        
        while (buffer.hasRemaining()) {
            int bytesRead = channel.read(buffer);
            
            if (bytesRead == -1) {
                return -1;
            }
            
            totalBytesRead += bytesRead;
            
            if (bytesRead == 0) {
                // No more data available right now, but connection is still open
                // For blocking behavior, we continue reading
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reading", e);
                }
            }
        }
        
        return totalBytesRead;
    }
    
    /**
     * Deserializes a message from bytes.
     */
    private Message deserializeMessage(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (Message) ois.readObject();
        } catch (Exception e) {
            logger.error("Failed to deserialize message", e);
            return null;
        }
    }
    
    /**
     * Sends a response message to the client.
     */
    private void sendResponse(SocketChannel channel, Message response) {
        try {
            // Serialize response
            byte[] responseBytes = serializeMessage(response);
            
            // Create buffer with length prefix
            ByteBuffer buffer = ByteBuffer.allocate(4 + responseBytes.length);
            buffer.putInt(responseBytes.length);
            buffer.put(responseBytes);
            buffer.flip();
            
            // Write to channel
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            
            logger.debug("Sent response: type={}, id={}", response.getType(), response.getMessageId());
            
        } catch (IOException e) {
            logger.error("Failed to send response", e);
        }
    }
    
    /**
     * Serializes a message to bytes.
     */
    private byte[] serializeMessage(Message message) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
            oos.flush();
            return baos.toByteArray();
        }
    }
    
    /**
     * Closes a channel and cancels its selection key.
     */
    private void closeChannel(SelectionKey key) {
        try {
            key.cancel();
            key.channel().close();
        } catch (IOException e) {
            logger.debug("Error closing channel", e);
        }
    }
}
