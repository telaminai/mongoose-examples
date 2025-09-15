package com.telamin.mongoose.example.howto;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.runtime.output.MessageSink;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.HandlerPipe;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating how to use HandlerPipe for in-VM communication between handlers.
 * 
 * This example shows:
 * - Creating HandlerPipe instances for in-VM message passing
 * - Using the sink side to publish messages
 * - Using the source side to receive messages via event subscription
 * - Lifecycle-aware caching and replay of events
 * - Data mapping and transformation capabilities
 */
public class HandlerPipeExample {

    public static void main(String[] args) throws Exception {
        System.out.println("HandlerPipe Example Started");
        
        // Create multiple pipes for different message types
        HandlerPipe<String> ordersPipe = HandlerPipe.<String>of("ordersPipe").cacheEventLog(true);
        HandlerPipe<String> notificationsPipe = HandlerPipe.<String>of("notificationsPipe").cacheEventLog(true);
        
        // Add data mapping to transform messages
        notificationsPipe.dataMapper(msg -> "NOTIFICATION: " + msg.toUpperCase());

        // Create processors that subscribe to the pipes
        OrderProcessor orderProcessor = new OrderProcessor();
        NotificationProcessor notificationProcessor = new NotificationProcessor();
        
        // Create a coordinator that publishes to pipes
        MessageCoordinator coordinator = new MessageCoordinator(ordersPipe, notificationsPipe);

        // Configure processor groups
        EventProcessorGroupConfig processorGroupConfig = EventProcessorGroupConfig.builder()
                .agentName("pipe-processor-agent")
                .put("order-processor", new EventProcessorConfig(orderProcessor))
                .put("notification-processor", new EventProcessorConfig(notificationProcessor))
                .put("coordinator", new EventProcessorConfig(coordinator))
                .build();

        // Configure the pipe sources as event feeds
        EventFeedConfig<?> ordersFeed = EventFeedConfig.builder()
                .instance(ordersPipe.getSource())
                .name(ordersPipe.getSource().getName())
                .broadcast(true)
                .agent("orders-agent", new BusySpinIdleStrategy())
                .build();

        EventFeedConfig<?> notificationsFeed = EventFeedConfig.builder()
                .instance(notificationsPipe.getSource())
                .name(notificationsPipe.getSource().getName())
                .broadcast(true)
                .agent("notifications-agent", new BusySpinIdleStrategy())
                .build();

        // Create sink to collect processed events
        InMemoryMessageSink sink = new InMemoryMessageSink();
        EventSinkConfig<MessageSink<?>> sinkConfig = EventSinkConfig.<MessageSink<?>>builder()
                .instance(sink)
                .name("pipe-sink")
                .build();

        // Configure and start the server
        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addProcessorGroup(processorGroupConfig)
                .addEventFeed(ordersFeed)
                .addEventFeed(notificationsFeed)
                .addEventSink(sinkConfig)
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            System.out.println("Demonstrating HandlerPipe communication...");

            // Publish some messages before the system is fully started (these will be cached)
            System.out.println("\nPublishing early messages (will be cached)...");
            ordersPipe.sink().accept("early-order-1");
            notificationsPipe.sink().accept("early-notification-1");

            // Give the system time to start
            Thread.sleep(500);

            // Publish more messages after startup
            System.out.println("\nPublishing messages after startup...");
            ordersPipe.sink().accept("order-123");
            ordersPipe.sink().accept("order-456");
            notificationsPipe.sink().accept("user-login");
            notificationsPipe.sink().accept("payment-received");

            // Trigger the coordinator to publish some messages
            coordinator.publishTestMessages();

            // Wait for processing
            Thread.sleep(2000);

            // Display results
            List<Object> messages = waitForMessages(sink, 6, 2, TimeUnit.SECONDS);
            System.out.println("\nProcessed messages:");
            messages.forEach(System.out::println);

        } finally {
            server.stop();
        }

        System.out.println("\nHandlerPipe Example Completed");
    }

    /**
     * Processor that handles order messages from the orders pipe
     */
    public static class OrderProcessor extends ObjectEventHandlerNode {
        private MessageSink<String> sink;
        private int orderCount = 0;

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @Override
        public void start() {
            // Subscribe to the orders pipe
            getContext().subscribeToNamedFeed("ordersPipe");
            System.out.println("OrderProcessor subscribed to ordersPipe");
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof String orderMessage) {
                orderCount++;
                String processedOrder = "PROCESSED_ORDER[" + orderCount + "]: " + orderMessage;
                System.out.println("OrderProcessor: " + processedOrder);
                
                if (sink != null) {
                    sink.accept(processedOrder);
                }
            }
            return true;
        }
    }

    /**
     * Processor that handles notification messages from the notifications pipe
     */
    public static class NotificationProcessor extends ObjectEventHandlerNode {
        private MessageSink<String> sink;
        private int notificationCount = 0;

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @Override
        public void start() {
            // Subscribe to the notifications pipe
            getContext().subscribeToNamedFeed("notificationsPipe");
            System.out.println("NotificationProcessor subscribed to notificationsPipe");
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof String notificationMessage) {
                notificationCount++;
                String processedNotification = "PROCESSED_NOTIFICATION[" + notificationCount + "]: " + notificationMessage;
                System.out.println("NotificationProcessor: " + processedNotification);
                
                if (sink != null) {
                    sink.accept(processedNotification);
                }
            }
            return true;
        }
    }

    /**
     * Coordinator that can publish messages to multiple pipes
     */
    public static class MessageCoordinator extends ObjectEventHandlerNode {
        private final HandlerPipe<String> ordersPipe;
        private final HandlerPipe<String> notificationsPipe;

        public MessageCoordinator(HandlerPipe<String> ordersPipe, HandlerPipe<String> notificationsPipe) {
            this.ordersPipe = ordersPipe;
            this.notificationsPipe = notificationsPipe;
        }

        @Override
        public void start() {
            System.out.println("MessageCoordinator started");
        }

        public void publishTestMessages() {
            System.out.println("Coordinator publishing test messages...");
            
            // Publish to orders pipe
            ordersPipe.sink().accept("coordinator-order-1");
            ordersPipe.sink().accept("coordinator-order-2");
            
            // Publish to notifications pipe
            notificationsPipe.sink().accept("coordinator-notification-1");
            notificationsPipe.sink().accept("coordinator-notification-2");
        }

        @Override
        protected boolean handleEvent(Object event) {
            // This coordinator doesn't handle external events in this example
            return true;
        }
    }

    private static List<Object> waitForMessages(InMemoryMessageSink sink, int minCount, long timeout, TimeUnit unit) throws Exception {
        long end = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < end) {
            List<Object> messages = sink.getMessages();
            if (messages.size() >= minCount) {
                return messages;
            }
            Thread.sleep(50);
        }
        return sink.getMessages();
    }
}