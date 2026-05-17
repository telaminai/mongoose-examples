package com.telamin.mongoose.example.howto;

import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventProcessorGroupConfig;
import com.telamin.mongoose.config.EventSinkConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;

import java.util.function.Function;

/**
 * Example demonstrating how to transform incoming feed events to a different type using value mapping.
 * 
 * This example shows:
 * - Creating a value mapper that transforms input events to target types
 * - Configuring an EventFeed with the value mapper
 * - Subscribing to the mapped events from a processor
 * - Processing the transformed events
 */
public class DataMappingExample {

    public static void main(String[] args) throws Exception {
        // Create event source with input events
        InMemoryEventSource<TestEvent_In> eventSource = new InMemoryEventSource<>();

        // Create the value mapper
        Function<TestEvent_In, Object> mapper = new TestDataMapper();

        // Configure the event feed with value mapper
        EventFeedConfig<TestEvent_In> feedConfig = EventFeedConfig
                .<TestEvent_In>builder()
                .instance(eventSource)
                .name("data-mapping-feed")
                .valueMapper(mapper)
                .build();

        // Create processor that handles mapped events
        TestEventProcessor processor = new TestEventProcessor();

        EventProcessorGroupConfig processorGroupConfig = EventProcessorGroupConfig.builder()
                .agentName("data-mapping-processor-agent")
                .put("data-mapping-processor", new EventProcessorConfig(processor))
                .build();

        // Create sink to collect processed events
        InMemoryMessageSink sink = new InMemoryMessageSink();
        EventSinkConfig<InMemoryMessageSink> sinkConfig = EventSinkConfig
                .<InMemoryMessageSink>builder()
                .instance(sink)
                .name("data-mapping-sink")
                .build();

        // Configure and start the server
        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addEventFeed(feedConfig)
                .addProcessorGroup(processorGroupConfig)
                .addEventSink(sinkConfig)
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            System.out.println("Data Mapping Example Started");
            System.out.println("Publishing input events...");

            // Publish some input events
            eventSource.publishNow(new TestEvent_In("Hello World"));
            eventSource.publishNow(new TestEvent_In("Data Mapping"));
            eventSource.publishNow(new TestEvent_In("Mongoose Example"));

            // Give some time for processing
            Thread.sleep(1000);

            // Display results
            System.out.println("\nProcessed Events:");
            TestEvent lastEvent = processor.getLastProcessedEvent();
            if (lastEvent != null) {
                System.out.println("Last processed event: " + lastEvent);
            }

            System.out.println("Total events processed: " + processor.getProcessedCount());

        } finally {
            server.stop();
        }

        System.out.println("\nData Mapping Example Completed");
    }

    // Input event type
    public record TestEvent_In(String message) {}

    // Target event type after mapping
    public record TestEvent(String transformedMessage, long timestamp) {}

    // Value mapper that transforms input to target type
    public static class TestDataMapper implements Function<TestEvent_In, Object> {
        @Override
        public Object apply(TestEvent_In input) {
            // Transform the input event to target type
            String transformed = "MAPPED: " + input.message().toUpperCase();
            return new TestEvent(transformed, System.currentTimeMillis());
        }
    }

    // Processor that handles the mapped events
    public static class TestEventProcessor extends ObjectEventHandlerNode {
        private volatile TestEvent lastProcessedEvent;
        private volatile int processedCount = 0;

        @Override
        public void start() {
            // Subscribe to the mapped output from the named feed
            getContext().subscribeToNamedFeed("data-mapping-feed");
        }

        @Override
        protected boolean handleEvent(Object event) {
            // After mapping, events arriving here are TestEvent instances
            if (event instanceof TestEvent testEvent) {
                lastProcessedEvent = testEvent;
                processedCount++;
                System.out.println("Processed mapped event: " + testEvent);
            }
            return true; // continue processing chain
        }

        public TestEvent getLastProcessedEvent() {
            return lastProcessedEvent;
        }

        public int getProcessedCount() {
            return processedCount;
        }
    }
}
