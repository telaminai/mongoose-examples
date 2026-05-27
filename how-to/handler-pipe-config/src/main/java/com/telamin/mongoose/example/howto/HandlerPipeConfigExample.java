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

        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                // The single addPipe call replaces the equivalent
                // HandlerPipe.of(...) + addEventFeed(...) ceremony in
                // the programmatic example.
                .addPipe(HandlerPipeConfig.builder()
                        .name(PIPE_NAME)
                        .broadcast(true)
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
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);
        try {
            // Give both processors time to start + the publisher to
            // receive the sink via @ServiceRegistered.
            Thread.sleep(300);

            // The publisher's start() hook has already fired three
            // messages into the pipe via the injected sink — they're
            // dispatched on the publisher-agent thread and consumed
            // on the subscriber-agent thread.
            Thread.sleep(800);

            System.out.println("\nSubscriber received " + subscriber.received + " event(s).");
        } finally {
            server.stop();
        }
    }

    /** Publisher processor — receives the pipe's sink via service
     *  injection on the name {@code orders.sink}, fires three values
     *  through it on start(). */
    public static class Publisher extends ObjectEventHandlerNode {
        private MessageSink<Object> sink;
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
