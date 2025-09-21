package com.telamin.mongoose.example.howto;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;

/**
 * Example demonstrating how to pin agent threads to specific CPU cores for improved performance.
 * 
 * This example shows:
 * - Configuring core pinning using ThreadConfig.coreId in MongooseServerConfig
 * - Setting up multiple agent groups with different core assignments
 * - Demonstrating the effect of core pinning on thread affinity
 * - Best practices for core assignment in multi-agent systems
 * - Verification approaches for checking pinning effectiveness
 */
public class CorePinExample {

    public static void main(String[] args) throws Exception {
        System.out.println("Core Pin Example Started");
        System.out.println("Demonstrating CPU core pinning for agent threads...");

        // Display system information
        displaySystemInfo();

        // Create event sources for different agent groups
        InMemoryEventSource<String> highPrioritySource = new InMemoryEventSource<>();
        InMemoryEventSource<String> mediumPrioritySource = new InMemoryEventSource<>();
        InMemoryEventSource<String> lowPrioritySource = new InMemoryEventSource<>();

        // Create processors for different priority levels
        CorePinnedProcessor highPriorityProcessor = new CorePinnedProcessor("HIGH_PRIORITY");
        CorePinnedProcessor mediumPriorityProcessor = new CorePinnedProcessor("MEDIUM_PRIORITY");
        CorePinnedProcessor lowPriorityProcessor = new CorePinnedProcessor("LOW_PRIORITY");

        // Create sink to collect processed events
        InMemoryMessageSink sink = new InMemoryMessageSink();

        // Configure server with core pinning
        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                // High priority processor group pinned to core 0 with BusySpinIdleStrategy
                .addThread(ThreadConfig.builder()
                        .agentName("high-priority-agent")
                        .idleStrategy(new BusySpinIdleStrategy())
                        .coreId(0) // Pin to core 0 for lowest latency
                        .build())
                .addProcessorGroup(EventProcessorGroupConfig.builder()
                        .agentName("high-priority-agent")
                        .put("high-priority-processor", new EventProcessorConfig(highPriorityProcessor))
                        .build())

                // Medium priority processor group pinned to core 1
                .addThread(ThreadConfig.builder()
                        .agentName("medium-priority-agent")
                        .idleStrategy(new SleepingMillisIdleStrategy(1))
                        .coreId(1) // Pin to core 1
                        .build())
                .addProcessorGroup(EventProcessorGroupConfig.builder()
                        .agentName("medium-priority-agent")
                        .put("medium-priority-processor", new EventProcessorConfig(mediumPriorityProcessor))
                        .build())

                // Low priority processor group pinned to core 2
                .addThread(ThreadConfig.builder()
                        .agentName("low-priority-agent")
                        .idleStrategy(new SleepingMillisIdleStrategy(10))
                        .coreId(2) // Pin to core 2
                        .build())
                .addProcessorGroup(EventProcessorGroupConfig.builder()
                        .agentName("low-priority-agent")
                        .put("low-priority-processor", new EventProcessorConfig(lowPriorityProcessor))
                        .build())

                // Event feeds with their own core assignments
                .addEventFeed(EventFeedConfig.builder()
                        .instance(highPrioritySource)
                        .name("high-priority-feed")
                        .agent("high-priority-feed-agent", new BusySpinIdleStrategy())
                        .build())
                .addThread(ThreadConfig.builder()
                        .agentName("high-priority-feed-agent")
                        .coreId(3) // Separate core for high priority feed
                        .build())

                .addEventFeed(EventFeedConfig.builder()
                        .instance(mediumPrioritySource)
                        .name("medium-priority-feed")
                        .agent("medium-priority-feed-agent", new SleepingMillisIdleStrategy(1))
                        .build())
                .addThread(ThreadConfig.builder()
                        .agentName("medium-priority-feed-agent")
                        .coreId(4) // Separate core for medium priority feed
                        .build())

                .addEventFeed(EventFeedConfig.builder()
                        .instance(lowPrioritySource)
                        .name("low-priority-feed")
                        .agent("low-priority-feed-agent", new SleepingMillisIdleStrategy(5))
                        .build())
                .addThread(ThreadConfig.builder()
                        .agentName("low-priority-feed-agent")
                        .coreId(5) // Separate core for low priority feed
                        .build())

                // Sink configuration
                .addEventSink(EventSinkConfig.<MessageSink<?>>builder()
                        .instance(sink)
                        .name("core-pin-sink")
                        .build())

                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            System.out.println("\nCore pinning configured. Server started with pinned agent threads.");
            System.out.println("Note: Actual OS-level pinning requires OpenHFT Affinity dependency.");
            
            // Wait for system to initialize
            Thread.sleep(1000);

            // Demonstrate workload distribution across pinned cores
            demonstrateWorkloadDistribution(highPrioritySource, mediumPrioritySource, lowPrioritySource);

            // Let the system process events
            Thread.sleep(3000);

            // Display results
            displayResults(sink, highPriorityProcessor, mediumPriorityProcessor, lowPriorityProcessor);

        } finally {
            server.stop();
        }

        System.out.println("\nCore Pin Example Completed");
    }

    private static void displaySystemInfo() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        System.out.println("\nSystem Information:");
        System.out.println("Available CPU cores: " + availableProcessors);
        System.out.println("Recommended core assignment:");
        System.out.println("  - High priority: Core 0 (BusySpinIdleStrategy)");
        System.out.println("  - Medium priority: Core 1 (SleepingMillisIdleStrategy 1ms)");
        System.out.println("  - Low priority: Core 2 (SleepingMillisIdleStrategy 10ms)");
        System.out.println("  - Feed agents: Cores 3-5");
        
        if (availableProcessors < 6) {
            System.out.println("WARNING: This example is configured for 6+ cores. " +
                             "With " + availableProcessors + " cores, some agents will share cores.");
        }
    }

    private static void demonstrateWorkloadDistribution(
            InMemoryEventSource<String> highPrioritySource,
            InMemoryEventSource<String> mediumPrioritySource,
            InMemoryEventSource<String> lowPrioritySource) {
        
        System.out.println("\nGenerating workload across different priority levels...");

        // High priority - frequent, low latency events
        for (int i = 0; i < 100; i++) {
            highPrioritySource.offer("HIGH_PRIORITY_EVENT_" + i);
        }

        // Medium priority - moderate frequency events
        for (int i = 0; i < 50; i++) {
            mediumPrioritySource.offer("MEDIUM_PRIORITY_EVENT_" + i);
        }

        // Low priority - infrequent, batch events
        for (int i = 0; i < 20; i++) {
            lowPrioritySource.offer("LOW_PRIORITY_EVENT_" + i);
        }

        System.out.println("Workload generated:");
        System.out.println("  - High priority: 100 events");
        System.out.println("  - Medium priority: 50 events");
        System.out.println("  - Low priority: 20 events");
    }

    private static void displayResults(
            InMemoryMessageSink sink,
            CorePinnedProcessor highPriorityProcessor,
            CorePinnedProcessor mediumPriorityProcessor,
            CorePinnedProcessor lowPriorityProcessor) {
        
        System.out.println("\nProcessing Results:");
        System.out.println("High Priority Processor:");
        System.out.println("  - Events processed: " + highPriorityProcessor.getProcessedCount());
        System.out.println("  - Thread ID: " + highPriorityProcessor.getThreadId());
        System.out.println("  - Thread name: " + highPriorityProcessor.getThreadName());

        System.out.println("Medium Priority Processor:");
        System.out.println("  - Events processed: " + mediumPriorityProcessor.getProcessedCount());
        System.out.println("  - Thread ID: " + mediumPriorityProcessor.getThreadId());
        System.out.println("  - Thread name: " + mediumPriorityProcessor.getThreadName());

        System.out.println("Low Priority Processor:");
        System.out.println("  - Events processed: " + lowPriorityProcessor.getProcessedCount());
        System.out.println("  - Thread ID: " + lowPriorityProcessor.getThreadId());
        System.out.println("  - Thread name: " + lowPriorityProcessor.getThreadName());

        System.out.println("Total messages in sink: " + sink.getMessages().size());
        
        System.out.println("\nCore Pinning Benefits:");
        System.out.println("  - Reduced context switching between cores");
        System.out.println("  - Better CPU cache locality");
        System.out.println("  - More predictable latency characteristics");
        System.out.println("  - Isolation of high-priority workloads");
    }

    /**
     * Processor that demonstrates core pinning effects
     */
    public static class CorePinnedProcessor extends ObjectEventHandlerNode {
        private final String processorType;
        private MessageSink<String> sink;
        private volatile int processedCount = 0;
        private volatile long threadId = -1;
        private volatile String threadName = "unknown";

        public CorePinnedProcessor(String processorType) {
            this.processorType = processorType;
        }

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @Override
        public void start() {
            // Capture thread information when processor starts
            Thread currentThread = Thread.currentThread();
            this.threadId = currentThread.getId();
            this.threadName = currentThread.getName();
            
            System.out.println(processorType + " processor started on thread: " + 
                             threadName + " (ID: " + threadId + ")");
            
            // Subscribe to the appropriate feed based on processor type
            String feedName = processorType.toLowerCase().replace("_", "-") + "-feed";
            getContext().subscribeToNamedFeed(feedName);
        }

        @Override
        protected boolean handleEvent(Object event) {
            processedCount++;
            
            if (sink != null && event instanceof String message) {
                String processedMessage = processorType + ": " + message + 
                                        " (processed by thread " + threadId + ")";
                sink.accept(processedMessage);
            }

            // Simulate different processing loads
            switch (processorType) {
                case "HIGH_PRIORITY":
                    // Minimal processing for low latency
                    break;
                case "MEDIUM_PRIORITY":
                    // Moderate processing
                    simulateWork(100); // 100 microseconds
                    break;
                case "LOW_PRIORITY":
                    // Heavy processing
                    simulateWork(1000); // 1 millisecond
                    break;
            }

            return true;
        }

        private void simulateWork(long microseconds) {
            long endTime = System.nanoTime() + (microseconds * 1000);
            while (System.nanoTime() < endTime) {
                // Busy wait to simulate CPU-intensive work
            }
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public long getThreadId() {
            return threadId;
        }

        public String getThreadName() {
            return threadName;
        }
    }

    /**
     * Utility class for core pinning configuration patterns
     */
    public static class CorePinningPatterns {
        
        /**
         * Creates a high-performance configuration with dedicated cores for critical paths
         */
        public static MongooseServerConfig.Builder createHighPerformanceConfig() {
            return MongooseServerConfig.builder()
                    // Critical path: Core 0 with BusySpinIdleStrategy
                    .addThread(ThreadConfig.builder()
                            .agentName("critical-agent")
                            .idleStrategy(new BusySpinIdleStrategy())
                            .coreId(0)
                            .build())
                    
                    // Event sources: Core 1
                    .addThread(ThreadConfig.builder()
                            .agentName("source-agent")
                            .idleStrategy(new BusySpinIdleStrategy())
                            .coreId(1)
                            .build())
                    
                    // Background processing: Core 2
                    .addThread(ThreadConfig.builder()
                            .agentName("background-agent")
                            .idleStrategy(new SleepingMillisIdleStrategy(1))
                            .coreId(2)
                            .build());
        }
        
        /**
         * Creates a NUMA-aware configuration for multi-socket systems
         */
        public static MongooseServerConfig.Builder createNUMAConfig() {
            return MongooseServerConfig.builder()
                    // NUMA node 0: Cores 0-3
                    .addThread(ThreadConfig.builder()
                            .agentName("numa0-critical")
                            .coreId(0)
                            .build())
                    .addThread(ThreadConfig.builder()
                            .agentName("numa0-processing")
                            .coreId(1)
                            .build())
                    
                    // NUMA node 1: Cores 4-7 (assuming 8-core dual-socket)
                    .addThread(ThreadConfig.builder()
                            .agentName("numa1-processing")
                            .coreId(4)
                            .build())
                    .addThread(ThreadConfig.builder()
                            .agentName("numa1-background")
                            .coreId(5)
                            .build());
        }
    }
}