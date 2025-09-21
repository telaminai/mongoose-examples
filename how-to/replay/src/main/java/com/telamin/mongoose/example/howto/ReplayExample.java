package com.telamin.mongoose.example.howto;

import com.fluxtion.agrona.concurrent.BackoffIdleStrategy;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.event.ReplayRecord;
import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;

import java.util.List;

/**
 * Example demonstrating deterministic replay with ReplayRecord and data-driven clock.
 * 
 * This example shows:
 * - Creating ReplayRecord objects with explicit wall-clock times
 * - Publishing replay records through event sources
 * - Handlers reading deterministic time via getContext().getClock()
 * - Reproducible behavior when replaying the same event stream
 * - Event unwrapping (handlers receive the wrapped event, not ReplayRecord)
 */
public class ReplayExample {

    public static void main(String[] args) throws Exception {
        System.out.println("Replay Example Started");
        System.out.println("Demonstrating deterministic replay with ReplayRecord...");

        // Create event source for replay records
        InMemoryEventSource<ReplayRecord> eventSource = new InMemoryEventSource<>();
        eventSource.setName("replayFeed");

        // Create handler that captures events and clock times
        ReplayCaptureHandler handler = new ReplayCaptureHandler("replayFeed");

        // Create sink to collect captured events
        InMemoryMessageSink sink = new InMemoryMessageSink();

        // Configure and start the server using the builder API
        EventProcessorGroupConfig processorGroupConfig = EventProcessorGroupConfig.builder()
                .agentName("replay-processor-agent")
                .put("replay-processor", new EventProcessorConfig(handler))
                .build();

        EventFeedConfig<?> feedConfig = EventFeedConfig.builder()
                .instance(eventSource)
                .name(eventSource.getName())
                .broadcast(true)
                .agent("source-agent", new BackoffIdleStrategy())
                .build();

        EventSinkConfig<MessageSink<?>> sinkConfig = EventSinkConfig.<MessageSink<?>>builder()
                .instance(sink)
                .name("replay-sink")
                .build();

        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addProcessorGroup(processorGroupConfig)
                .addEventFeed(feedConfig)
                .addEventSink(sinkConfig)
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            // Wait for system to initialize
            Thread.sleep(500);

            System.out.println("\nDemonstrating replay scenarios...");

            // Demonstrate basic replay
            demonstrateBasicReplay(eventSource, sink);

            // Demonstrate replay with different time sequences
            demonstrateTimeSequenceReplay(eventSource, sink);

            // Demonstrate replay reproducibility
            demonstrateReplayReproducibility(eventSource, sink);

        } finally {
            server.stop();
        }

        System.out.println("\nReplay Example Completed");
    }

    private static void demonstrateBasicReplay(InMemoryEventSource<ReplayRecord> eventSource, InMemoryMessageSink sink) throws InterruptedException {
        System.out.println("\n=== Basic Replay Demo ===");

        // Clear previous messages
        sink.clear();

        // Create replay records with explicit timestamps
        long baseTime = 1_696_000_000_000L; // epoch millis

        ReplayRecord r1 = new ReplayRecord();
        r1.setEvent("first-event");
        r1.setWallClockTime(baseTime);

        ReplayRecord r2 = new ReplayRecord();
        r2.setEvent("second-event");
        r2.setWallClockTime(baseTime + 1000); // 1 second later

        ReplayRecord r3 = new ReplayRecord();
        r3.setEvent("third-event");
        r3.setWallClockTime(baseTime + 2500); // 2.5 seconds later

        // Publish replay records
        System.out.println("Publishing replay records with explicit timestamps...");
        eventSource.offer(r1);
        eventSource.offer(r2);
        eventSource.offer(r3);

        // Wait for processing
        Thread.sleep(500);

        // Display captured events
        List<Object> messages = sink.getMessages();
        System.out.println("Captured events:");
        messages.forEach(System.out::println);
    }

    private static void demonstrateTimeSequenceReplay(InMemoryEventSource<ReplayRecord> eventSource, InMemoryMessageSink sink) throws InterruptedException {
        System.out.println("\n=== Time Sequence Replay Demo ===");

        // Clear previous messages
        sink.clear();

        // Create replay records with non-sequential timestamps (out of order)
        long baseTime = 1_700_000_000_000L;

        ReplayRecord[] records = {
            createReplayRecord("event-at-T+3000", baseTime + 3000),
            createReplayRecord("event-at-T+1000", baseTime + 1000),
            createReplayRecord("event-at-T+5000", baseTime + 5000),
            createReplayRecord("event-at-T+2000", baseTime + 2000),
            createReplayRecord("event-at-T+0", baseTime)
        };

        System.out.println("Publishing events with out-of-order timestamps...");
        for (ReplayRecord record : records) {
            eventSource.offer(record);
            Thread.sleep(50); // Small delay between publications
        }

        // Wait for processing
        Thread.sleep(500);

        // Display captured events
        List<Object> messages = sink.getMessages();
        System.out.println("Captured events (note: processed in publication order, but with original timestamps):");
        messages.forEach(System.out::println);
    }

    private static void demonstrateReplayReproducibility(InMemoryEventSource<ReplayRecord> eventSource, InMemoryMessageSink sink) throws InterruptedException {
        System.out.println("\n=== Replay Reproducibility Demo ===");

        // Create identical replay sequences
        long baseTime = 1_650_000_000_000L;
        ReplayRecord[] sequence = {
            createReplayRecord("reproducible-event-1", baseTime + 100),
            createReplayRecord("reproducible-event-2", baseTime + 200),
            createReplayRecord("reproducible-event-3", baseTime + 300)
        };

        // First run
        System.out.println("First replay run:");
        sink.clear();
        for (ReplayRecord record : sequence) {
            eventSource.offer(record);
        }
        Thread.sleep(300);
        List<Object> firstRun = List.copyOf(sink.getMessages());
        firstRun.forEach(msg -> System.out.println("  " + msg));

        // Second run (identical)
        System.out.println("Second replay run (should be identical):");
        sink.clear();
        for (ReplayRecord record : sequence) {
            eventSource.offer(record);
        }
        Thread.sleep(300);
        List<Object> secondRun = List.copyOf(sink.getMessages());
        secondRun.forEach(msg -> System.out.println("  " + msg));

        // Verify reproducibility
        boolean identical = firstRun.equals(secondRun);
        System.out.println("Replay runs are identical: " + identical);
    }

    private static ReplayRecord createReplayRecord(String event, long wallClockTime) {
        ReplayRecord record = new ReplayRecord();
        record.setEvent(event);
        record.setWallClockTime(wallClockTime);
        return record;
    }

    /**
     * Handler that captures events and their associated clock times for replay demonstration
     */
    public static class ReplayCaptureHandler extends ObjectEventHandlerNode {
        private final String feedName;
        private MessageSink<String> sink;
        private int eventCount = 0;

        public ReplayCaptureHandler(String feedName) {
            this.feedName = feedName;
        }

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @Override
        public void start() {
            getContext().subscribeToNamedFeed(feedName);
            System.out.println("ReplayCaptureHandler subscribed to feed: " + feedName);
        }

        @Override
        protected boolean handleEvent(Object event) {
            eventCount++;

            // Get the data-driven clock time (deterministic from ReplayRecord)
            long clockTime = getContext().getClock().getWallClockTime();

            // Create capture message
            String captureMessage = String.format("event[%d]=%s, clock_time=%d", 
                eventCount, event, clockTime);

            System.out.println("Captured: " + captureMessage);

            if (sink != null) {
                sink.accept(captureMessage);
            }

            return true;
        }

        public int getEventCount() {
            return eventCount;
        }

        public void resetCount() {
            eventCount = 0;
        }
    }

    /**
     * Utility class for creating test replay scenarios
     */
    public static class ReplayScenario {
        private final String name;
        private final ReplayRecord[] records;

        public ReplayScenario(String name, ReplayRecord[] records) {
            this.name = name;
            this.records = records;
        }

        public void execute(InMemoryEventSource<ReplayRecord> eventSource) throws InterruptedException {
            System.out.println("Executing scenario: " + name);
            for (ReplayRecord record : records) {
                eventSource.offer(record);
                Thread.sleep(10); // Small delay between events
            }
        }

        public static ReplayScenario createTimeSeriesScenario() {
            long baseTime = System.currentTimeMillis() - 86400000; // 24 hours ago
            ReplayRecord[] records = new ReplayRecord[5];

            for (int i = 0; i < records.length; i++) {
                records[i] = new ReplayRecord();
                records[i].setEvent("time-series-event-" + (i + 1));
                records[i].setWallClockTime(baseTime + (i * 3600000)); // 1 hour intervals
            }

            return new ReplayScenario("Time Series", records);
        }

        public static ReplayScenario createBurstScenario() {
            long baseTime = 1_600_000_000_000L;
            ReplayRecord[] records = new ReplayRecord[10];

            for (int i = 0; i < records.length; i++) {
                records[i] = new ReplayRecord();
                records[i].setEvent("burst-event-" + (i + 1));
                records[i].setWallClockTime(baseTime + (i * 100)); // 100ms intervals
            }

            return new ReplayScenario("Burst Events", records);
        }
    }
}
