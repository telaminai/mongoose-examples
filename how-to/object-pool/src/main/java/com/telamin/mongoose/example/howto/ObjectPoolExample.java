package com.telamin.mongoose.example.howto;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;
import com.telamin.mongoose.service.extension.AbstractEventSourceService;
import com.telamin.mongoose.service.pool.impl.BasePoolAware;
import com.telamin.mongoose.service.pool.ObjectPool;
import com.telamin.mongoose.service.pool.ObjectPoolsRegistry;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating how to use Object Pool for zero-GC high-performance event publishing.
 * 
 * This example shows:
 * - Creating pooled message types that extend BasePoolAware
 * - Using ObjectPoolsRegistry to create and manage object pools
 * - Publishing pooled events with automatic reference counting
 * - Zero-GC event processing at high rates
 * - Performance monitoring with GC and memory statistics
 */
public class ObjectPoolExample {

    public static void main(String[] args) throws Exception {
        System.out.println("Object Pool Example Started");
        System.out.println("Demonstrating zero-GC high-performance event publishing...");

        // Create the pooled event source
        PooledEventSource eventSource = new PooledEventSource();

        // Create processor that handles pooled events
        PooledMessageProcessor processor = new PooledMessageProcessor();

        // Create sink to collect processed events
        InMemoryMessageSink sink = new InMemoryMessageSink();

        // Configure and start the server using the simpler API
        MongooseServerConfig serverConfig = new MongooseServerConfig()
                .addProcessor("pool-processor", processor, "pool-processor-agent")
                .addEventSource(eventSource, "pooled-event-source", true);

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            // Wait for system to initialize
            Thread.sleep(500);

            System.out.println("\nStarting high-rate publishing demonstration...");

            // Demonstrate different publishing patterns
            demonstrateBasicPooling(eventSource);
            demonstrateHighRatePublishing(eventSource, processor);
            demonstrateBurstPublishing(eventSource, processor);

        } finally {
            server.stop();
        }

        System.out.println("\nObject Pool Example Completed");
    }

    private static void demonstrateBasicPooling(PooledEventSource eventSource) throws InterruptedException {
        System.out.println("\n=== Basic Pooling Demo ===");

        // Publish a few messages to show basic pooling
        eventSource.publish("basic-message-1");
        eventSource.publish("basic-message-2");
        eventSource.publish("basic-message-3");

        Thread.sleep(100);
        System.out.println("Published 3 basic pooled messages");
    }

    private static void demonstrateHighRatePublishing(PooledEventSource eventSource, PooledMessageProcessor processor) throws InterruptedException {
        System.out.println("\n=== High-Rate Publishing Demo ===");

        long startTime = System.nanoTime();
        long startGcCount = getTotalGcCount();
        long startMemory = getUsedMemoryMB();

        int messageCount = 100_000;

        for (int i = 0; i < messageCount; i++) {
            eventSource.publish("high-rate-message-" + i);

            // Small pause to prevent overwhelming the system
            if (i % 10_000 == 0 && i > 0) {
                Thread.sleep(1);
            }
        }

        // Wait for processing to complete
        Thread.sleep(1000);

        long endTime = System.nanoTime();
        long endGcCount = getTotalGcCount();
        long endMemory = getUsedMemoryMB();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        long messagesPerSecond = (messageCount * 1000L) / Math.max(durationMs, 1);

        System.out.println("Published " + messageCount + " messages in " + durationMs + " ms");
        System.out.println("Rate: " + messagesPerSecond + " messages/second");
        System.out.println("GC count increase: " + (endGcCount - startGcCount));
        System.out.println("Memory change: " + (endMemory - startMemory) + " MB");
        System.out.println("Processed messages: " + processor.getProcessedCount());
    }

    private static void demonstrateBurstPublishing(PooledEventSource eventSource, PooledMessageProcessor processor) throws InterruptedException {
        System.out.println("\n=== Burst Publishing Demo ===");

        processor.resetCounters();

        // Publish in bursts
        for (int burst = 0; burst < 5; burst++) {
            System.out.println("Publishing burst " + (burst + 1) + "...");

            for (int i = 0; i < 1000; i++) {
                eventSource.publish("burst-" + burst + "-message-" + i);
            }

            Thread.sleep(100); // Pause between bursts
        }

        Thread.sleep(500);
        System.out.println("Burst publishing completed. Total processed: " + processor.getProcessedCount());
    }

    private static long getTotalGcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();
    }

    private static long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    /**
     * Pooled message type that extends BasePoolAware for automatic pool management
     */
    public static class PooledMessage extends BasePoolAware {
        public String value;
        public long timestamp;
        public int sequenceNumber;

        @Override
        public String toString() {
            return "PooledMessage{value='" + value + "', timestamp=" + timestamp + ", seq=" + sequenceNumber + '}';
        }
    }

    /**
     * Event source that publishes pooled messages using ObjectPool
     */
    public static class PooledEventSource extends AbstractEventSourceService<PooledMessage> {
        private ObjectPool<PooledMessage> pool;
        private int sequenceCounter = 0;

        public PooledEventSource() {
            super("pooled-event-source");
        }

        @ServiceRegistered
        public void setObjectPoolsRegistry(ObjectPoolsRegistry objectPoolsRegistry, String name) {
            System.out.println("ObjectPoolsRegistry injected into PooledEventSource");

            // Create object pool with factory and reset function
            this.pool = objectPoolsRegistry.getOrCreate(
                    PooledMessage.class,
                    PooledMessage::new,  // Factory function
                    pm -> {              // Reset function
                        pm.value = null;
                        pm.timestamp = 0;
                        pm.sequenceNumber = 0;
                    }
            );

            System.out.println("ObjectPool created for PooledMessage");
        }

        /**
         * Publish a message value. The framework acquires and releases references as the
         * message passes through queues and consumers; the object is returned to the pool
         * automatically at end-of-cycle once all references are released.
         */
        public void publish(String value) {
            if (pool == null) {
                System.err.println("Pool not initialized yet");
                return;
            }

            PooledMessage msg = pool.acquire();
            msg.value = value;
            msg.timestamp = System.currentTimeMillis();
            msg.sequenceNumber = ++sequenceCounter;

            output.publish(msg);
            // No manual release needed; the framework manages references after publish
        }
    }

    /**
     * Processor that handles pooled messages
     */
    public static class PooledMessageProcessor extends ObjectEventHandlerNode {
        private MessageSink<String> sink;
        private volatile long processedCount = 0;
        private volatile long lastReportTime = System.currentTimeMillis();
        private volatile long lastReportCount = 0;

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof PooledMessage pooledMessage) {
                processedCount++;

                String processedMessage = "PROCESSED: " + pooledMessage.toString();

                // Report progress every 10,000 messages
                if (processedCount % 10_000 == 0) {
                    long currentTime = System.currentTimeMillis();
                    long timeDiff = currentTime - lastReportTime;
                    long countDiff = processedCount - lastReportCount;

                    if (timeDiff > 0) {
                        long rate = (countDiff * 1000) / timeDiff;
                        System.out.println("Processed " + processedCount + " messages, rate: " + rate + " msg/s, " +
                                         "heap: " + getUsedMemoryMB() + " MB, GC: " + getTotalGcCount());
                    }

                    lastReportTime = currentTime;
                    lastReportCount = processedCount;
                }

                if (sink != null && processedCount <= 10) {
                    // Only send first few messages to sink to avoid overwhelming it
                    sink.accept(processedMessage);
                }
            }

            return true;
        }

        public long getProcessedCount() {
            return processedCount;
        }

        public void resetCounters() {
            processedCount = 0;
            lastReportTime = System.currentTimeMillis();
            lastReportCount = 0;
        }
    }
}
