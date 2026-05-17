/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.telamin.mongoose.example.aeron;

import com.fluxtion.dataflow.serverplugin.testsupport.MongooseTestHarness;
import com.fluxtion.server.plugin.connector.aeron.AeronArchiveEventSource;
import com.fluxtion.server.plugin.connector.aeron.AeronMessageSink;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Asserts that {@link AeronRoundTripExample}'s pipeline delivers events end-to-end:
 * producer feed → {@link AeronMessageSink} → embedded Aeron IPC →
 * {@link AeronArchiveEventSource} → handler → in-memory capture sink.
 */
class AeronRoundTripExampleTest {

    private MediaDriver mediaDriver;

    @BeforeEach
    void launchDriver() {
        mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
    }

    @AfterEach
    void closeDriver() {
        if (mediaDriver != null) mediaDriver.close();
    }


    /**
     * Subscribes only to the "producer" feed. Without this filter, both handlers
     * receive every broadcast feed — including aeron-in — which creates a
     * write-back loop on the aeron channel.
     */
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
        public void start() {
            getContext().subscribeToNamedFeed("producer");
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof String s && aeronSink != null) {
                aeronSink.accept(s);
            }
            return true;
        }
    }

    /** Subscribes only to the aeron-in feed. */
    public static class CapturingHandler extends ObjectEventHandlerNode {
        @SuppressWarnings("unchecked")
        private MessageSink<String> captureSink;

        @ServiceRegistered
        public void wire(MessageSink<?> sink, String name) {
            if ("captured".equals(name)) {
                this.captureSink = (MessageSink<String>) sink;
            }
        }

        @Override
        public void start() {
            getContext().subscribeToNamedFeed("aeron-in");
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof String s && captureSink != null) {
                captureSink.accept(s);
            }
            return true;
        }
    }

    @Test
    void in_memory_to_aeron_ipc_to_in_memory_capture() {
        InMemoryEventSource<String> producer = new InMemoryEventSource<>();
        InMemoryMessageSink captured = new InMemoryMessageSink();

        AeronArchiveEventSource aeronSource = new AeronArchiveEventSource("aeron-live-feed");
        aeronSource.setMode(AeronArchiveEventSource.Mode.LIVE);
        aeronSource.setChannel(AeronRoundTripExample.CHANNEL);
        aeronSource.setStreamId(AeronRoundTripExample.STREAM_ID);
        aeronSource.setAeronDirectoryName(mediaDriver.aeronDirectoryName());

        AeronMessageSink aeronSink = new AeronMessageSink();
        aeronSink.setChannel(AeronRoundTripExample.CHANNEL);
        aeronSink.setStreamId(AeronRoundTripExample.STREAM_ID);
        aeronSink.setAeronDirectoryName(mediaDriver.aeronDirectoryName());

        try (MongooseTestHarness h = MongooseTestHarness.builder()
                .feed("producer", producer, "producer-agent", new SleepingMillisIdleStrategy(1), false)
                .feed("aeron-in", aeronSource, "aeron-source-agent", new SleepingMillisIdleStrategy(1), false)
                .sink("aeron-out", aeronSink)
                .sink("captured", captured)
                .processor("producer-processor", "producer-handler", new ProducerHandler())
                .processor("consumer-processor", "consumer-handler", new CapturingHandler())
                .start()) {

            producer.offer("hello");
            producer.offer("aeron");
            producer.offer("world");

            h.awaitCondition(() -> captured.getMessages().size() >= 3);

            assertEquals(List.of("hello", "aeron", "world"), captured.getMessages());
        }
    }
}
