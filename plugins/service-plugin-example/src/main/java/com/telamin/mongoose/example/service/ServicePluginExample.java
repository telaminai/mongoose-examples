/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.service;

import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.ServiceConfig;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;

import java.util.concurrent.TimeUnit;

/**
 * Example application demonstrating how to create and use custom service plugins with Mongoose.
 * <p>
 * This example shows:
 * <ul>
 *   <li>Creating a simple lifecycle-based service (GreetingService)</li>
 *   <li>Creating a worker service that runs on its own agent thread (MetricsCollectorService)</li>
 *   <li>Injecting services into event handlers using @ServiceRegistered</li>
 *   <li>Configuring and registering services with MongooseServerConfig</li>
 * </ul>
 */
public class ServicePluginExample {

    public static void main(String[] args) throws Exception {
        // Create the services
        GreetingService greetingService = new GreetingService("Welcome", "!!!");
        MetricsCollectorService metricsService = new MetricsCollectorService(3_000_000_000L); // 3 seconds

        // Create the event handler that will use the services
        ServiceAwareEventHandler handler = new ServiceAwareEventHandler();

        // Configure the services
        ServiceConfig<GreetingService> greetingServiceConfig = ServiceConfig.<GreetingService>builder()
                .service(greetingService)
                .serviceClass(GreetingService.class)
                .name("greetingService")
                .build();

        ServiceConfig<MetricsCollectorService> metricsServiceConfig = ServiceConfig.<MetricsCollectorService>builder()
                .service(metricsService)
                .serviceClass(MetricsCollectorService.class)
                .name("metricsService")
                .agent("metrics-agent", new SleepingMillisIdleStrategy(100)) // Worker service needs an agent
                .build();

        // Configure the event processor
        EventProcessorConfig eventProcessorConfig = EventProcessorConfig.builder()
                .customHandler(handler)
                .name("service-aware-handler")
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

        // Build the Mongoose server configuration
        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addProcessor("processor-agent", eventProcessorConfig)
                .addEventFeed(eventFeed)
                .addService(greetingServiceConfig)
                .addService(metricsServiceConfig)
                .build();

        // Boot the Mongoose server
        System.out.println("Starting Mongoose server with custom service plugins...");
        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            // Wait a moment for services to be injected
            TimeUnit.MILLISECONDS.sleep(500);

            // Publish some test events
            System.out.println("\nPublishing test events...\n");

            eventSource.offer("Alice");
            TimeUnit.MILLISECONDS.sleep(1000);

            eventSource.offer("Bob");
            TimeUnit.MILLISECONDS.sleep(1000);

            eventSource.offer("Charlie");
            TimeUnit.MILLISECONDS.sleep(1000);

            eventSource.offer("Diana");
            TimeUnit.MILLISECONDS.sleep(1000);

            eventSource.offer("Eve");
            TimeUnit.MILLISECONDS.sleep(1000);

            // Wait for metrics to be collected and reported
            System.out.println("\nWaiting for metrics collection...");
            TimeUnit.SECONDS.sleep(8);

            // Publish a few more events
            System.out.println("\nPublishing more test events...\n");

            eventSource.offer("Frank");
            eventSource.offer("Grace");
            eventSource.offer("Henry");

            // Wait for final metrics
            TimeUnit.SECONDS.sleep(4);

            System.out.println("\nTest completed. Check the logs for service lifecycle and metrics reports.");
            System.out.println("Final message count: " + handler.getProcessedMessageCount());

        } finally {
            // Shutdown the server
            System.out.println("\nShutting down Mongoose server...");
            server.stop();
            System.out.println("Server stopped.");
        }
    }
}