/*
 * SPDX-FileCopyrightText: © 2026 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.telamin.mongoose.example.howto;

import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.PerformanceMonitoringConfig;
import com.telamin.mongoose.dispatch.EventToQueuePublisher;
import com.telamin.mongoose.service.EventFlowService;
import com.telamin.mongoose.service.EventSubscriptionKey;
import com.telamin.mongoose.service.counters.MongooseCountersService;

import java.util.TreeMap;

/**
 * Self-contained example showing how to enable Mongoose performance monitoring
 * and read the counters it produces.
 *
 * <p>What this example does:
 * <ol>
 *     <li>Boots a Mongoose server with {@code performanceMonitoring.enabled=true}
 *         (programmatic config — the YAML equivalent is two lines in the server
 *         descriptor; see the README).</li>
 *     <li>Registers a tiny {@link EventFlowService} feed via
 *         {@link MongooseServer#registerEventSource(String, com.telamin.mongoose.service.EventSource)}.</li>
 *     <li>Publishes 1,000 events directly through the feed's
 *         {@link EventToQueuePublisher} — the Phase 2 counter wrap site.</li>
 *     <li>Walks {@link MongooseCountersService#forEachCounter} and prints every
 *         counter the runtime has populated.</li>
 * </ol>
 *
 * <p>Run with:
 * <pre>
 *     mvn -pl how-to/performance-monitoring -am package
 *     mvn -pl how-to/performance-monitoring exec:exec
 * </pre>
 *
 * <p>To see per-processor / per-node counts as well, bind a
 * {@code PerformanceMonitorAudit} inside the Fluxtion compile lambda. See the
 * how-to doc {@code example/how-to/how-to-performance-monitoring.md} for the
 * full pattern.
 */
public class PerformanceMonitoringExample {

    /**
     * Minimal event feed: captures the {@link EventToQueuePublisher} that
     * Mongoose hands it at registration so we can call {@code publish(...)}
     * directly. This is the same shape mongoose-core's
     * {@code CountersHotPathIntegrationTest} uses — keeps the example free of
     * the agent-hosted / pending-queue plumbing in {@code InMemoryEventSource}.
     */
    public static class StringFeed implements EventFlowService<String> {
        public EventToQueuePublisher<String> publisher;

        @Override
        public void setEventToQueuePublisher(EventToQueuePublisher<String> targetQueue) {
            this.publisher = targetQueue;
        }

        @Override public void subscribe(EventSubscriptionKey<String> k)   { /* no-op */ }
        @Override public void unSubscribe(EventSubscriptionKey<String> k) { /* no-op */ }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Mongoose performance-monitoring example ===");

        // Enable performance monitoring. The YAML equivalent for a deployment
        // descriptor is:
        //
        //     performanceMonitoring:
        //       enabled: true
        //       counterBufferKb: 256
        //
        PerformanceMonitoringConfig perf = new PerformanceMonitoringConfig();
        perf.setEnabled(true);
        perf.setCounterBufferKb(64);  // 64 KB → ~512 counters; plenty for this example

        MongooseServerConfig cfg = new MongooseServerConfig();
        cfg.setPerformanceMonitoring(perf);

        MongooseServer server = MongooseServer.bootServer(cfg);

        try {
            // Register a feed AFTER boot — EventFlowManager.registerEventSource
            // allocates the counter-wired EventToQueuePublisher and calls
            // setEventToQueuePublisher() on the feed, which captures it for
            // direct publishing.
            StringFeed feed = new StringFeed();
            server.registerEventSource("ticker-feed", feed);

            // Drive 1k events through the publisher. publish() is the Phase 2
            // counter wrap site — every call bumps feed.ticker-feed.published.
            for (int i = 0; i < 1_000; i++) {
                feed.publisher.publish("tick-" + i);
            }

            // Pull the counters service out of the registry and dump.
            MongooseCountersService counters = (MongooseCountersService) server
                    .registeredServices()
                    .get(MongooseCountersService.SERVICE_NAME)
                    .instance();

            System.out.println();
            System.out.println("Counters (operational=" + counters.isOperational() + "):");
            System.out.println("----------------------------------------------------------");
            // Sort by label for deterministic output.
            TreeMap<String, Long> sorted = new TreeMap<>();
            counters.forEachCounter((id, label, value) -> sorted.put(label, value));
            for (var e : sorted.entrySet()) {
                System.out.printf("  %-55s %d%n", e.getKey(), e.getValue());
            }
            System.out.println("----------------------------------------------------------");
            System.out.println();
            System.out.println("Counter labels follow a flat convention:");
            System.out.println("  feed.{name}.published     — events the feed pushed");
            System.out.println("  group.{name}.processed    — work cycles the agent dispatched");
            System.out.println("  group.{name}.idleCycles   — work cycles that found nothing to do");
            System.out.println("  queue.{path}.depth        — per-subscriber dispatch queue depth");
            System.out.println();
            System.out.println("To view these live in a browser, add the svc-admin-web plugin and");
            System.out.println("point http://127.0.0.1:8181/ at the Dashboard view.");
        } finally {
            server.stop();
        }
    }
}
