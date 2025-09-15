package com.telamin.mongoose.example.howto;

import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;

/**
 * Example demonstrating how to inject initial configuration into event processors.
 * 
 * This example shows:
 * - Creating a processor that implements ConfigListener
 * - Using ConfigMap and ConfigKey for type-safe configuration access
 * - Configuring processors with initial values using EventProcessorConfig.putConfig()
 * - How configuration is delivered at server boot time
 */
public class InjectingConfigIntoProcessorExample {

    public static void main(String[] args) throws Exception {
        // Create event source
        InMemoryEventSource<String> eventSource = new InMemoryEventSource<>();

        // Configure the event feed
        EventFeedConfig<String> feedConfig = EventFeedConfig
                .<String>builder()
                .instance(eventSource)
                .name("config-feed")
                .build();

        // Create processor with configuration
        ConfigAwareProcessor processor = new ConfigAwareProcessor();

        EventProcessorConfig<?> processorConfig = EventProcessorConfig.builder()
                .customHandler(processor)
                .putConfig("greeting", "Hello from Config")
                .putConfig("threshold", 5)
                .putConfig("enabled", true)
                .putConfig("multiplier", 2.5)
                .build();

        EventProcessorGroupConfig processorGroupConfig = EventProcessorGroupConfig.builder()
                .agentName("config-processor-agent")
                .put("config-processor", processorConfig)
                .build();

        // Create sink to collect processed events
        InMemoryMessageSink sink = new InMemoryMessageSink();
        EventSinkConfig<InMemoryMessageSink> sinkConfig = EventSinkConfig
                .<InMemoryMessageSink>builder()
                .instance(sink)
                .name("config-sink")
                .build();

        // Configure and start the server
        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addEventFeed(feedConfig)
                .addProcessorGroup(processorGroupConfig)
                .addEventSink(sinkConfig)
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            System.out.println("Config Injection Example Started");
            System.out.println("Configuration values:");
            System.out.println("  greeting: " + processor.getGreeting());
            System.out.println("  threshold: " + processor.getThreshold());
            System.out.println("  enabled: " + processor.isEnabled());
            System.out.println("  multiplier: " + processor.getMultiplier());

            // Publish some test events
            System.out.println("\nPublishing test events...");
            eventSource.offer("event1");
            eventSource.offer("event2");
            eventSource.offer("event3");
            eventSource.offer("event4");
            eventSource.offer("event5");
            eventSource.offer("event6"); // This should trigger threshold behavior

            // Give some time for processing
            Thread.sleep(1000);

            System.out.println("\nProcessed " + processor.getProcessedCount() + " events");
            System.out.println("Threshold reached: " + processor.isThresholdReached());

        } finally {
            server.stop();
        }

        System.out.println("\nConfig Injection Example Completed");
    }

    /**
     * Processor that demonstrates configuration injection using ConfigListener
     */
    public static class ConfigAwareProcessor extends ObjectEventHandlerNode implements ConfigListener {
        
        // Configuration fields
        private String greeting = "Default Greeting";
        private int threshold = 10;
        private boolean enabled = false;
        private double multiplier = 1.0;
        
        // Runtime state
        private int processedCount = 0;
        private boolean thresholdReached = false;

        @Override
        public boolean initialConfig(ConfigMap config) {
            System.out.println("Receiving initial configuration...");
            
            // Use ConfigKey for type-safe configuration access
            this.greeting = config.getOrDefault(ConfigKey.of("greeting", String.class), "Default Greeting");
            this.threshold = config.getOrDefault(ConfigKey.of("threshold", Integer.class), 10);
            this.enabled = config.getOrDefault(ConfigKey.of("enabled", Boolean.class), false);
            this.multiplier = config.getOrDefault(ConfigKey.of("multiplier", Double.class), 1.0);
            
            System.out.println("Configuration loaded successfully");
            return true;
        }

        @Override
        public void start() {
            getContext().subscribeToNamedFeed("config-feed");
            System.out.println("Processor started with configuration:");
            System.out.println("  enabled: " + enabled);
            System.out.println("  threshold: " + threshold);
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (!enabled) {
                System.out.println("Processor disabled, ignoring event: " + event);
                return true;
            }

            if (event instanceof String message) {
                processedCount++;
                
                String processedMessage = greeting + " - " + message + 
                    " (count: " + processedCount + ", multiplied: " + (processedCount * multiplier) + ")";
                
                System.out.println("Processed: " + processedMessage);
                
                // Check threshold
                if (processedCount >= threshold && !thresholdReached) {
                    thresholdReached = true;
                    System.out.println("*** THRESHOLD REACHED! Processed " + processedCount + " events ***");
                }
            }
            
            return true;
        }

        // Getters for testing/demonstration
        public String getGreeting() { return greeting; }
        public int getThreshold() { return threshold; }
        public boolean isEnabled() { return enabled; }
        public double getMultiplier() { return multiplier; }
        public int getProcessedCount() { return processedCount; }
        public boolean isThresholdReached() { return thresholdReached; }
    }
}