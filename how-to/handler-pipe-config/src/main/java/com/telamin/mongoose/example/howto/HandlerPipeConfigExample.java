/*
 * SPDX-FileCopyrightText: © 2026 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.telamin.mongoose.example.howto;

import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventProcessorGroupConfig;
import com.telamin.mongoose.config.HandlerPipeConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.ServiceConfig;
import com.telamin.mongoose.plugin.svc.adminweb.WebAdminService;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

/**
 * Declarative in-VM pipe between two processors using {@link HandlerPipeConfig}.
 *
 * <p>Compare with the sibling {@code handler-pipe} example which builds the
 * same shape programmatically — that one calls {@code HandlerPipe.of(...)},
 * wraps {@code getSource()} as an {@link com.telamin.mongoose.config.EventFeedConfig
 * EventFeedConfig}, and adds it via {@code addEventFeed(...)}. Same outcome,
 * roughly 20 lines of plumbing per pipe.
 *
 * <p>This example collapses that to:
 * <pre>{@code
 * .addPipe(HandlerPipeConfig.builder()
 *         .name("orders")
 *         .broadcast(true)
 *         .agent("pipe-agent", new SleepingMillisIdleStrategy(1))
 *         .build())
 * }</pre>
 *
 * <p>The single {@code addPipe} call registers both halves of the pipe:
 * <ul>
 *   <li>A {@code NamedFeed} named {@code "orders"} — subscribed to via
 *       {@code subscribeToNamedFeed("orders")}.</li>
 *   <li>A {@code MessageSink} named {@code "orders.sink"} — discovered by
 *       publishers via {@code @ServiceRegistered void onSink(MessageSink, String name)}
 *       (matched against the suffixed name).</li>
 * </ul>
 *
 * <p>Cross-thread safe — the underlying {@code InMemoryEventSource} is
 * agent-hosted, so producer + consumer on different agent groups work
 * without an explicit synchronization step.
 */
public class HandlerPipeConfigExample {

    /** Logical pipe name. Subscribers use this name; publishers see the
     *  sink under {@code orders.sink} (default ".sink" suffix). */
    private static final String PIPE_NAME = "orders";

    public static void main(String[] args) throws Exception {
        System.out.println("HandlerPipeConfig example — booting…");

        Publisher publisher = new Publisher();
        Subscriber subscriber = new Subscriber();

        // Wire the admin web on a deliberately uncommon port so it
        // doesn't collide with the operator's other Mongoose
        // deployments. Browse to the URL printed below once boot
        // settles.
        WebAdminService adminWeb = new WebAdminService();
        adminWeb.setListenPort(8186);

        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                // The single addPipe call replaces the equivalent
                // HandlerPipe.of(...) + addEventFeed(...) ceremony in
                // the programmatic example.
                .addPipe(HandlerPipeConfig.builder()
                        .name(PIPE_NAME)
                        // broadcast=false → only processors that
                        // explicitly subscribeToNamedFeed("orders")
                        // receive events. Without this, the Publisher
                        // processor would also appear as a consumer
                        // in the topology view (broadcast delivers to
                        // every registered processor regardless of
                        // explicit subscription), which is confusing
                        // for a point-to-point pipe demo.
                        .broadcast(false)
                        .agent("pipe-agent", new SleepingMillisIdleStrategy(1))
                        .build())
                .addProcessorGroup(EventProcessorGroupConfig.builder()
                        .agentName("publisher-agent")
                        .put("publisher", new EventProcessorConfig(publisher))
                        .build())
                .addProcessorGroup(EventProcessorGroupConfig.builder()
                        .agentName("subscriber-agent")
                        .put("subscriber", new EventProcessorConfig(subscriber))
                        .build())
                .addService(ServiceConfig.builder()
                        .service(adminWeb)
                        .serviceClass(WebAdminService.class)
                        .name("adminWeb")
                        .build())
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);
        try {
            Thread.sleep(300);
            // Initial three values fire from Publisher.start().
            Thread.sleep(800);
            System.out.println("\nSubscriber received " + subscriber.received + " event(s).");

            System.out.println("\n──────────────────────────────────────────────────");
            System.out.println("  Admin web up:  http://127.0.0.1:8186");
            System.out.println();
            System.out.println("  Open the page in a browser to see:");
            System.out.println("    • Overview → Pipes card showing '" + PIPE_NAME + "'");
            System.out.println("      with both endpoint names + agent + flags");
            System.out.println("    • Topology view → diamond-shaped pipe node");
            System.out.println("      with arrows in from publisher-agent");
            System.out.println("      and arrows out to subscriber-agent");
            System.out.println("    • /api/pipes — raw JSON of the pipe registry");
            System.out.println();
            System.out.println("  Holding for 5 minutes. Ctrl-C to exit.");
            System.out.println("──────────────────────────────────────────────────");

            // Keep firing periodic values so the topology view shows
            // ongoing activity (rate pulses on the pipe-agent group).
            for (int i = 0; i < 300; i++) {
                Thread.sleep(1000);
                if (publisher.sink != null) {
                    publisher.sink.accept("tick-" + i);
                }
            }
        } finally {
            server.stop();
        }
    }

    /** Publisher processor — receives the pipe's sink via service
     *  injection on the name {@code orders.sink}, fires three values
     *  through it on start(). */
    public static class Publisher extends ObjectEventHandlerNode {
        // public so the main() loop below can fire periodic values
        // through the same injected sink. Demo-shape only.
        public volatile MessageSink<Object> sink;
        private boolean fired;

        @SuppressWarnings({"unchecked", "rawtypes"})
        @ServiceRegistered
        public void onSink(MessageSink sink, String name) {
            // Default ".sink" suffix on the sink-side name.
            if ((PIPE_NAME + ".sink").equals(name)) {
                this.sink = sink;
                System.out.println("Publisher: received sink for '" + name + "'");
            }
        }

        @Override
        public void start() {
            // The first start() fires three values straight into the
            // pipe. They're observed by the Subscriber on a different
            // agent thread without explicit synchronization — the pipe's
            // agent-hosted InMemoryEventSource handles the handoff.
            if (sink != null && !fired) {
                fired = true;
                sink.accept("first-order");
                sink.accept("second-order");
                sink.accept("third-order");
                System.out.println("Publisher: sent 3 values into the pipe");
            }
        }

        @Override
        protected boolean handleEvent(Object event) { return true; }
    }

    /** Subscriber processor — subscribes to the pipe by its name on
     *  start(), prints each received event. */
    public static class Subscriber extends ObjectEventHandlerNode {
        public volatile int received;

        @Override
        public void start() {
            getContext().subscribeToNamedFeed(PIPE_NAME);
            System.out.println("Subscriber: subscribed to '" + PIPE_NAME + "'");
        }

        @Override
        protected boolean handleEvent(Object event) {
            // Ignore framework lifecycle events (EventLogConfig etc) that
            // arrive on the broadcast path — count + print only the
            // pipe payload values.
            if (!(event instanceof String s)) return true;
            received++;
            System.out.println("Subscriber: received '" + s + "' (#" + received + ")");
            return true;
        }
    }
}
