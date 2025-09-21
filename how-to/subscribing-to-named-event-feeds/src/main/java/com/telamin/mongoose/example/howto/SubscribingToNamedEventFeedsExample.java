/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.howto;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.telamin.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating how to subscribe to specific named EventFeeds and ignore others.
 * 
 * This example creates three named event feeds (prices, orders, news) but only subscribes
 * to two of them (prices and news), effectively ignoring the orders feed.
 */
public class SubscribingToNamedEventFeedsExample {

    public static void main(String[] args) throws Exception {
        // In-memory sink to collect processed messages
        InMemoryMessageSink memSink = new InMemoryMessageSink();

        // Create three named in-memory event sources
        InMemoryEventSource<String> prices = new InMemoryEventSource<>();
        prices.setCacheEventLog(true);
        InMemoryEventSource<String> orders = new InMemoryEventSource<>();
        orders.setCacheEventLog(true);
        InMemoryEventSource<String> news = new InMemoryEventSource<>();
        news.setCacheEventLog(true);

        // Create processor that only forwards events from feeds: prices, news
        NamedFeedsFilterHandler filterHandler = new NamedFeedsFilterHandler(Set.of("prices", "news"));

        EventProcessorGroupConfig processorGroup = EventProcessorGroupConfig.builder()
                .agentName("processor-agent")
                .put("filter-processor", new EventProcessorConfig(filterHandler))
                .build();

        // Build EventFeed configs with names
        EventFeedConfig<?> pricesFeed = EventFeedConfig.builder()
                .instance(prices)
                .name("prices")
                .agent("prices-agent", new BusySpinIdleStrategy())
                .build();

        EventFeedConfig<?> ordersFeed = EventFeedConfig.builder()
                .instance(orders)
                .name("orders")
                .agent("orders-agent", new BusySpinIdleStrategy())
                .build();

        EventFeedConfig<?> newsFeed = EventFeedConfig.builder()
                .instance(news)
                .name("news")
                .agent("news-agent", new BusySpinIdleStrategy())
                .build();

        EventSinkConfig<MessageSink<?>> sinkCfg = EventSinkConfig.<MessageSink<?>>builder()
                .instance(memSink)
                .name("memSink")
                .build();

        MongooseServerConfig mongooseServerConfig = MongooseServerConfig.builder()
                .addProcessorGroup(processorGroup)
                .addEventFeed(pricesFeed)
                .addEventFeed(ordersFeed)
                .addEventFeed(newsFeed)
                .addEventSink(sinkCfg)
                .build();

        MongooseServer server = MongooseServer.bootServer(mongooseServerConfig);

        try {
            System.out.println("Publishing events to all three feeds...");
            
            // Publish events to all feeds
            prices.offer("p1");
            prices.offer("p2");
            orders.offer("o1"); // This will be ignored
            orders.offer("o2"); // This will be ignored
            news.offer("n1");
            news.offer("n2");

            // Wait for messages to be processed
            waitForMessages(memSink, 4, 1, TimeUnit.SECONDS);

        } finally {
            server.stop();
        }

        System.out.println("\nReceived messages (should only include prices and news, not orders):");
        List<Object> messages = waitForMessages(memSink, 4, 1, TimeUnit.SECONDS);
        messages.forEach(System.out::println);
        
        System.out.println("\nExpected: p1, p2, n1, n2 (orders o1, o2 are ignored)");
        System.out.println("Actual count: " + messages.size() + " messages");
    }

    private static List<Object> waitForMessages(InMemoryMessageSink sink, int minCount, long timeout, TimeUnit unit) throws Exception {
        long end = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < end) {
            List<Object> lines = sink.getMessages();
            if (lines.size() >= minCount) {
                return lines;
            }
            Thread.sleep(50);
        }
        return sink.getMessages();
    }
}