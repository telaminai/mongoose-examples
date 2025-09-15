package com.telamin.mongoose.example.howto;

import com.fluxtion.runtime.StaticEventProcessor;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;
import com.telamin.mongoose.dispatch.AbstractEventToInvocationStrategy;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;

import java.util.ArrayList;
import java.util.List;

/**
 * Example demonstrating how to write custom EventToInvokeStrategy implementations.
 * 
 * This example shows:
 * - Creating custom EventToInvokeStrategy by extending AbstractEventToInvocationStrategy
 * - Filtering which processors can receive events using isValidTarget()
 * - Transforming events before delivery using dispatchEvent()
 * - Using strongly-typed callbacks instead of generic onEvent()
 * - Registering custom strategies via MongooseServerConfig
 * - Different strategy patterns: filtering, transformation, and routing
 */
public class WritingCustomEventToInvokeStrategyExample {

    public static void main(String[] args) throws Exception {
        System.out.println("Writing Custom EventToInvokeStrategy Example Started");
        System.out.println("Demonstrating custom event dispatch strategies...");

        // Create event sources for different types of events
        InMemoryEventSource<Object> eventSource = new InMemoryEventSource<>();

        // Create processors that implement different marker interfaces
        StringProcessorImpl stringProcessor = new StringProcessorImpl();
        NumberProcessorImpl numberProcessor = new NumberProcessorImpl();
        GenericProcessorImpl genericProcessor = new GenericProcessorImpl();

        // Create sink to collect processed events
        InMemoryMessageSink sink = new InMemoryMessageSink();

        // Configure server with custom event-to-invoke strategy
        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                // Register custom strategy for event dispatch
                .onEventInvokeStrategy(CustomEventToInvokeStrategy::new)
                
                .addProcessorGroup(EventProcessorGroupConfig.builder()
                        .agentName("custom-strategy-agent")
                        .put("string-processor", new EventProcessorConfig(stringProcessor))
                        .put("number-processor", new EventProcessorConfig(numberProcessor))
                        .put("generic-processor", new EventProcessorConfig(genericProcessor))
                        .build())

                .addEventFeed(EventFeedConfig.builder()
                        .instance(eventSource)
                        .name("custom-strategy-feed")
                        .build())

                .addEventSink(EventSinkConfig.<MessageSink<?>>builder()
                        .instance(sink)
                        .name("custom-strategy-sink")
                        .build())

                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            System.out.println("\nServer started with custom EventToInvokeStrategy");
            
            // Wait for system to initialize
            Thread.sleep(1000);

            // Demonstrate different event types and routing
            demonstrateCustomEventRouting(eventSource);

            // Let the system process events
            Thread.sleep(2000);

            // Display results
            displayResults(sink, stringProcessor, numberProcessor, genericProcessor);

        } finally {
            server.stop();
        }

        System.out.println("\nWriting Custom EventToInvokeStrategy Example Completed");
    }

    private static void demonstrateCustomEventRouting(InMemoryEventSource<Object> eventSource) {
        System.out.println("\nPublishing different types of events...");

        // String events - should be routed to StringProcessor with transformation
        eventSource.offer("hello");
        eventSource.offer("world");
        eventSource.offer("custom strategy");

        // Number events - should be routed to NumberProcessor with transformation
        eventSource.offer(42);
        eventSource.offer(3.14);
        eventSource.offer(100L);

        // Custom events - should be routed to GenericProcessor
        eventSource.offer(new CustomEvent("custom-event-1", "data1"));
        eventSource.offer(new CustomEvent("custom-event-2", "data2"));

        // Events that don't match any specific processor - should be filtered out
        eventSource.offer(new Object());
        eventSource.offer(new ArrayList<>());

        System.out.println("Published various event types:");
        System.out.println("  - String events: 3");
        System.out.println("  - Number events: 3");
        System.out.println("  - Custom events: 2");
        System.out.println("  - Other events: 2 (should be filtered out)");
    }

    private static void displayResults(
            InMemoryMessageSink sink,
            StringProcessorImpl stringProcessor,
            NumberProcessorImpl numberProcessor,
            GenericProcessorImpl genericProcessor) {
        
        System.out.println("\nProcessing Results:");
        System.out.println("String Processor:");
        System.out.println("  - Events processed: " + stringProcessor.getProcessedCount());
        System.out.println("  - Processed strings: " + stringProcessor.getProcessedStrings());

        System.out.println("Number Processor:");
        System.out.println("  - Events processed: " + numberProcessor.getProcessedCount());
        System.out.println("  - Processed numbers: " + numberProcessor.getProcessedNumbers());

        System.out.println("Generic Processor:");
        System.out.println("  - Events processed: " + genericProcessor.getProcessedCount());
        System.out.println("  - Processed events: " + genericProcessor.getProcessedEvents());

        System.out.println("Total messages in sink: " + sink.getMessages().size());
        
        System.out.println("\nCustom Strategy Benefits:");
        System.out.println("  - Strongly-typed event callbacks");
        System.out.println("  - Event filtering and routing");
        System.out.println("  - Event transformation before delivery");
        System.out.println("  - Centralized dispatch logic");
    }

    /**
     * Custom EventToInvokeStrategy that demonstrates filtering, transformation, and routing
     */
    public static class CustomEventToInvokeStrategy extends AbstractEventToInvocationStrategy {

        @Override
        protected void dispatchEvent(Object event, StaticEventProcessor eventProcessor) {
            // Route String events to StringProcessor with transformation
            if (event instanceof String str && eventProcessor instanceof StringProcessor stringProc) {
                // Transform: convert to uppercase
                stringProc.onString(str.toUpperCase());
                return;
            }

            // Route Number events to NumberProcessor with transformation
            if (event instanceof Number num && eventProcessor instanceof NumberProcessor numberProc) {
                // Transform: convert to double and multiply by 2
                numberProc.onNumber(num.doubleValue() * 2.0);
                return;
            }

            // Route CustomEvent to GenericProcessor with transformation
            if (event instanceof CustomEvent customEvent && eventProcessor instanceof GenericProcessor genericProc) {
                // Transform: create enriched event
                EnrichedCustomEvent enriched = new EnrichedCustomEvent(
                    customEvent.getName(),
                    customEvent.getData(),
                    System.currentTimeMillis(),
                    "PROCESSED"
                );
                genericProc.onCustomEvent(enriched);
                return;
            }

            // For any other valid processor, use default onEvent dispatch
            if (isValidTarget(eventProcessor)) {
                eventProcessor.onEvent(event);
            }
            
            // Events that don't match any processor are silently filtered out
        }

        @Override
        protected boolean isValidTarget(StaticEventProcessor eventProcessor) {
            // Only accept processors that implement our marker interfaces
            return eventProcessor instanceof StringProcessor ||
                   eventProcessor instanceof NumberProcessor ||
                   eventProcessor instanceof GenericProcessor;
        }
    }

    /**
     * Marker interface for processors that handle String events
     */
    public interface StringProcessor {
        void onString(String str);
    }

    /**
     * Marker interface for processors that handle Number events
     */
    public interface NumberProcessor {
        void onNumber(double number);
    }

    /**
     * Marker interface for processors that handle CustomEvent events
     */
    public interface GenericProcessor {
        void onCustomEvent(EnrichedCustomEvent event);
    }

    /**
     * Processor that handles String events with strongly-typed callback
     */
    public static class StringProcessorImpl extends ObjectEventHandlerNode implements StringProcessor {
        private MessageSink<String> sink;
        private volatile int processedCount = 0;
        private final List<String> processedStrings = new ArrayList<>();

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @Override
        public void start() {
            getContext().subscribeToNamedFeed("custom-strategy-feed");
        }

        @Override
        public void onString(String str) {
            processedCount++;
            processedStrings.add(str);
            
            if (sink != null) {
                sink.accept("StringProcessor processed: " + str);
            }
            
            System.out.println("StringProcessor received: " + str);
        }

        @Override
        protected boolean handleEvent(Object event) {
            // This should not be called due to our custom strategy
            System.out.println("StringProcessor.handleEvent called with: " + event);
            return true;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public List<String> getProcessedStrings() {
            return new ArrayList<>(processedStrings);
        }
    }

    /**
     * Processor that handles Number events with strongly-typed callback
     */
    public static class NumberProcessorImpl extends ObjectEventHandlerNode implements NumberProcessor {
        private MessageSink<String> sink;
        private volatile int processedCount = 0;
        private final List<Double> processedNumbers = new ArrayList<>();

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @Override
        public void start() {
            getContext().subscribeToNamedFeed("custom-strategy-feed");
        }

        @Override
        public void onNumber(double number) {
            processedCount++;
            processedNumbers.add(number);
            
            if (sink != null) {
                sink.accept("NumberProcessor processed: " + number);
            }
            
            System.out.println("NumberProcessor received: " + number);
        }

        @Override
        protected boolean handleEvent(Object event) {
            // This should not be called due to our custom strategy
            System.out.println("NumberProcessor.handleEvent called with: " + event);
            return true;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public List<Double> getProcessedNumbers() {
            return new ArrayList<>(processedNumbers);
        }
    }

    /**
     * Processor that handles CustomEvent events with strongly-typed callback
     */
    public static class GenericProcessorImpl extends ObjectEventHandlerNode implements GenericProcessor {
        private MessageSink<String> sink;
        private volatile int processedCount = 0;
        private final List<EnrichedCustomEvent> processedEvents = new ArrayList<>();

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @Override
        public void start() {
            getContext().subscribeToNamedFeed("custom-strategy-feed");
        }

        @Override
        public void onCustomEvent(EnrichedCustomEvent event) {
            processedCount++;
            processedEvents.add(event);
            
            if (sink != null) {
                sink.accept("GenericProcessor processed: " + event);
            }
            
            System.out.println("GenericProcessor received: " + event);
        }

        @Override
        protected boolean handleEvent(Object event) {
            // This should not be called due to our custom strategy
            System.out.println("GenericProcessor.handleEvent called with: " + event);
            return true;
        }

        public int getProcessedCount() {
            return processedCount;
        }

        public List<EnrichedCustomEvent> getProcessedEvents() {
            return new ArrayList<>(processedEvents);
        }
    }

    /**
     * Custom event type for demonstration
     */
    public static class CustomEvent {
        private final String name;
        private final String data;

        public CustomEvent(String name, String data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public String getData() {
            return data;
        }

        @Override
        public String toString() {
            return "CustomEvent{name='" + name + "', data='" + data + "'}";
        }
    }

    /**
     * Enriched custom event created by the strategy
     */
    public static class EnrichedCustomEvent extends CustomEvent {
        private final long timestamp;
        private final String status;

        public EnrichedCustomEvent(String name, String data, long timestamp, String status) {
            super(name, data);
            this.timestamp = timestamp;
            this.status = status;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return "EnrichedCustomEvent{name='" + getName() + "', data='" + getData() + 
                   "', timestamp=" + timestamp + ", status='" + status + "'}";
        }
    }
}