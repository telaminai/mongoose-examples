/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.fivemin;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.runtime.audit.LogRecordListener;
import com.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Sample showing how to subscribe to EventFeeds with different names and ignore
 * those that do not match.
 */
public class FiveMinuteTutorial {

    public static void main(String[] args) throws Exception {
        // In-memory sink
        InMemoryMessageSink memSink = new InMemoryMessageSink();

        // Feeds: three named in-memory sources
        InMemoryEventSource<String> prices = new InMemoryEventSource<>();
        InMemoryEventSource<String> orders = new InMemoryEventSource<>();
        InMemoryEventSource<String> news = new InMemoryEventSource<>();

        // Processor that only forwards events from feeds: prices, news
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

        LogRecordListener logListener = rec -> {};
        MongooseServer server = MongooseServer.bootServer(mongooseServerConfig, logListener);
        try {

            prices.offer("p1");
            prices.offer("p2");
            orders.offer("o1"); // ignored filter mismatch
            orders.offer("o2"); // ignored filter mismatch
            news.offer("n1");
            news.offer("n2");

            // Wait for sink messages; should include only p1,p2,n1,n2
        } finally {
            server.stop();
        }

        System.out.println("received:");
        waitForMessages(memSink, 4, 1, TimeUnit.SECONDS).forEach(System.out::println);
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
