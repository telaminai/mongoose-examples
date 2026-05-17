/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.telamin.mongoose.example.aeron;

import com.fluxtion.server.plugin.connector.aeron.AeronArchiveEventSource;
import com.fluxtion.server.plugin.connector.aeron.AeronMessageSink;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventSinkConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.util.concurrent.TimeUnit;

/**
 * Round-trip example: an in-memory producer feed publishes strings, a handler
 * forwards them onto an Aeron IPC channel via {@link AeronMessageSink}, and a
 * second handler reads them back from {@link AeronArchiveEventSource} in
 * {@code LIVE} mode. Both sides share an embedded Aeron {@code MediaDriver},
 * so no external infrastructure is required.
 *
 * <p>Wire diagram:
 * <pre>{@code
 *   InMemoryEventSource "producer"
 *           │ String events
 *           ▼
 *   ProducerHandler ──► AeronMessageSink ───┐
 *                                            │ aeron:ipc, stream 10
 *                                            ▼
 *                            AeronArchiveEventSource (LIVE)
 *                                            │
 *                                            ▼
 *                                    ConsumerHandler  ──► System.out
 * }</pre>
 *
 * <p>Run with: {@code mvn -pl plugins/connector-aeron-example exec:java}, or
 * see the included integration test for the assertion shape.
 */
public final class AeronRoundTripExample {

    public static final String CHANNEL = "aeron:ipc";
    public static final int STREAM_ID = 10;

    /** Forwards each String from the producer feed onto the Aeron sink. */
    public static class ProducerHandler extends ObjectEventHandlerNode {
        @SuppressWarnings("unchecked")
        private MessageSink<String> aeronSink;

        @ServiceRegistered
        public void wire(MessageSink<?> sink, String name) {
            if ("aeron-out".equals(name)) {
                this.aeronSink = (MessageSink<String>) sink;
            }
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof String s && aeronSink != null) {
                System.out.println("PRODUCED: " + s);
                aeronSink.accept(s);
            }
            return true;
        }
    }

    /** Reads each String from the Aeron source and prints it. */
    public static class ConsumerHandler extends ObjectEventHandlerNode {
        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof String s) {
                System.out.println("CONSUMED: " + s);
            }
            return true;
        }
    }

    /** Build the server config used by both {@code main} and the test. */
    public static MongooseServerConfig buildConfig(InMemoryEventSource<String> producerFeed) {
        AeronArchiveEventSource aeronSource = new AeronArchiveEventSource("aeron-live-feed");
        aeronSource.setMode(AeronArchiveEventSource.Mode.LIVE);
        aeronSource.setChannel(CHANNEL);
        aeronSource.setStreamId(STREAM_ID);
        aeronSource.setLaunchEmbeddedDriver(true);  // dev / example only

        AeronMessageSink aeronSink = new AeronMessageSink();
        aeronSink.setChannel(CHANNEL);
        aeronSink.setStreamId(STREAM_ID);
        // Reuse the same CnC directory the embedded driver creates; the source
        // sets aeronDirectoryName on launch — for tests we let the harness wait.

        EventFeedConfig<?> producerCfg = EventFeedConfig.builder()
                .instance(producerFeed)
                .name("producer")
                .broadcast(true)
                .agent("producer-agent", new SleepingMillisIdleStrategy(1))
                .build();

        EventFeedConfig<?> aeronFeedCfg = EventFeedConfig.builder()
                .instance(aeronSource)
                .name("aeron-in")
                .broadcast(true)
                .agent("aeron-source-agent", new SleepingMillisIdleStrategy(1))
                .build();

        EventSinkConfig<?> aeronSinkCfg = EventSinkConfig.builder()
                .instance(aeronSink)
                .name("aeron-out")
                .build();

        EventProcessorConfig<?> producerProc = EventProcessorConfig.builder()
                .customHandler(new ProducerHandler())
                .name("producer-handler")
                .build();

        EventProcessorConfig<?> consumerProc = EventProcessorConfig.builder()
                .customHandler(new ConsumerHandler())
                .name("consumer-handler")
                .build();

        return MongooseServerConfig.builder()
                .addEventFeed(producerCfg)
                .addEventFeed(aeronFeedCfg)
                .addEventSink(aeronSinkCfg)
                .addProcessor("producer-processor", "producer-handler", producerProc)
                .addProcessor("consumer-processor", "consumer-handler", consumerProc)
                .build();
    }

    public static void main(String[] args) throws InterruptedException {
        InMemoryEventSource<String> producer = new InMemoryEventSource<>();
        MongooseServer server = MongooseServer.bootServer(buildConfig(producer));
        try {
            producer.offer("hello");
            producer.offer("aeron");
            producer.offer("world");
            // give the LIVE subscription a moment to deliver — in production code
            // you'd keep the server running and rely on graceful shutdown.
            TimeUnit.SECONDS.sleep(1);
        } finally {
            server.stop();
        }
    }
}
