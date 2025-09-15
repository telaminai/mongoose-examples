package com.telamin.mongoose.example.messagesink;

import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventSinkConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;

import java.util.concurrent.TimeUnit;

/**
 * Example application demonstrating how to configure and use a custom MessageSink with Mongoose.
 * <p>
 * This example:
 * <ul>
 *   <li>Creates a simple event handler that processes string events</li>
 *   <li>Sets up an in-memory event source to publish events</li>
 *   <li>Configures a custom ConsoleMessageSink with specific formatting options</li>
 *   <li>Boots a Mongoose server with these components</li>
 *   <li>Publishes sample events to demonstrate the message sink in action</li>
 * </ul>
 */
public class MessageSinkExample {

    public static void main(String[] args) throws Exception {
        // Create a custom console message sink with specific configuration
        ConsoleMessageSink consoleSink = new ConsoleMessageSink();
        consoleSink.setPrefix("MONGOOSE-EVENT");
        consoleSink.setIncludeTimestamp(true);
        consoleSink.setTimestampFormat("HH:mm:ss.SSS");

        // Create a simple event handler that processes string events and forwards them to the sink
        var handler = new MyObjectEventHandlerNode();

        // Configure the event processor
        var eventProcessorConfig = EventProcessorConfig.builder()
                .customHandler(handler)
                .name("string-event-handler")
                .build();

        // Create an in-memory event source for publishing test events
        InMemoryEventSource<String> eventSource = new InMemoryEventSource<>();

        // Configure the event feed
        EventFeedConfig<?> eventFeed = EventFeedConfig.builder()
                .instance(eventSource)
                .name("test-events")
                .agent("event-source-agent", new SleepingMillisIdleStrategy(10))
                .broadcast(true)  // Broadcast to all processors without explicit subscription
                .build();

        // Configure the message sink
        EventSinkConfig<?> sinkConfig = EventSinkConfig.builder()
                .instance(consoleSink)
                .name("console-sink")
                .build();

        // Build the Mongoose server configuration
        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addProcessor("processor-agent", eventProcessorConfig)
                .addEventFeed(eventFeed)
                .addEventSink(sinkConfig)
                .build();

        // Boot the Mongoose server
        System.out.println("Starting Mongoose server with custom ConsoleMessageSink...");
        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            // Publish some test events
            System.out.println("\nPublishing test events...\n");

            eventSource.offer("Hello, Mongoose!");
            TimeUnit.MILLISECONDS.sleep(500);

            eventSource.offer("This is a test message");
            TimeUnit.MILLISECONDS.sleep(500);

            eventSource.offer("Custom message sink example");
            TimeUnit.MILLISECONDS.sleep(500);

            eventSource.offer("Events are formatted with timestamps");
            TimeUnit.MILLISECONDS.sleep(500);

            System.out.println("\nTest events published. Press Ctrl+C to exit or wait 5 seconds for automatic shutdown.");
            TimeUnit.SECONDS.sleep(5);
        } finally {
            // Shutdown the server
            System.out.println("\nShutting down Mongoose server...");
            server.stop();
            System.out.println("Server stopped.");
        }
    }

}
