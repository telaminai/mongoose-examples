package com.telamin.mongoose.example.howto;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;
import com.telamin.mongoose.service.scheduler.SchedulerService;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Example demonstrating re-entrant publishing with processAsNewEventCycle and SchedulerService.
 * 
 * This example shows:
 * - Using getContext().processAsNewEventCycle() to inject events back into the processing cycle
 * - Combining SchedulerService with processAsNewEventCycle for periodic re-entry
 * - Creating self-sustaining event loops within processors
 * - Controlling re-entrant publishing with termination conditions
 * - Different patterns: periodic publishing, countdown sequences, and burst generation
 */
public class SchedulerProcessAsNewEventCycleExample {

    public static void main(String[] args) throws Exception {
        System.out.println("Scheduler processAsNewEventCycle Example Started");
        System.out.println("Demonstrating re-entrant publishing patterns...");

        // Create sink to collect processed events
        InMemoryMessageSink sink = new InMemoryMessageSink();

        // Configure and start the server using the simpler API
        MongooseServerConfig serverConfig = new MongooseServerConfig();
        serverConfig.addEventSource(sink, "memory-event-source", true);
        serverConfig.addProcessor("process-as-new-event-cycle-processor", new ObjectEventHandlerNode(), "process-as-new-event-cycle-agent");

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            // Wait for system to initialize
            Thread.sleep(500);

            System.out.println("\nDemonstrating re-entrant publishing scenarios...");

            // Demonstrate basic re-entrant publishing
            demonstrateBasicReEntrant(server);

            // Demonstrate countdown sequence
            demonstrateCountdownSequence(server);

            // Demonstrate burst publishing
            demonstrateBurstPublishing(server);

        } finally {
            server.stop();
        }

        System.out.println("\nScheduler processAsNewEventCycle Example Completed");
    }

    private static void demonstrateBasicReEntrant(MongooseServer server) throws InterruptedException {
        System.out.println("\n=== Basic Re-Entrant Publishing Demo ===");

        // Create handler with basic re-entrant publishing
        BasicReEntrantHandler handler = new BasicReEntrantHandler()
                .setRepublishWaitMillis(100)
                .setMaxCount(5)
                .setThrowOnMax(false);

        // Add processor to running server (simplified for demo)
        System.out.println("Starting basic re-entrant handler...");
        handler.schedulerRegistered(new MockSchedulerService(), "test");
        handler.start();

        // Wait for completion
        long timeoutMs = 2_000;
        long start = System.currentTimeMillis();
        while (handler.getCount() < handler.getMaxCount() && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(50);
        }

        System.out.println("Basic re-entrant completed. Final count: " + handler.getCount());
    }

    private static void demonstrateCountdownSequence(MongooseServer server) throws InterruptedException {
        System.out.println("\n=== Countdown Sequence Demo ===");

        CountdownHandler handler = new CountdownHandler()
                .setStartValue(10)
                .setDelayMillis(150);

        System.out.println("Starting countdown from " + handler.getStartValue() + "...");
        handler.schedulerRegistered(new MockSchedulerService(), "test");
        handler.start();

        // Wait for countdown to complete
        Thread.sleep(2000);
        System.out.println("Countdown completed.");
    }

    private static void demonstrateBurstPublishing(MongooseServer server) throws InterruptedException {
        System.out.println("\n=== Burst Publishing Demo ===");

        BurstPublisher handler = new BurstPublisher()
                .setBurstSize(3)
                .setBurstCount(2)
                .setBurstDelayMillis(200)
                .setEventDelayMillis(50);

        System.out.println("Starting burst publishing...");
        handler.schedulerRegistered(new MockSchedulerService(), "test");
        handler.start();

        // Wait for bursts to complete
        Thread.sleep(1500);
        System.out.println("Burst publishing completed.");
    }

    /**
     * Basic re-entrant handler that publishes events in a loop
     */
    @Getter
    public static class BasicReEntrantHandler extends ObjectEventHandlerNode {
        private SchedulerService schedulerService;
        private int count = 0;
        private long republishWaitMillis = 100;
        private int maxCount = 10;
        private boolean throwOnMax = false;

        // Fluent setters
        public BasicReEntrantHandler setRepublishWaitMillis(long republishWaitMillis) {
            this.republishWaitMillis = republishWaitMillis;
            return this;
        }

        public BasicReEntrantHandler setMaxCount(int maxCount) {
            this.maxCount = maxCount;
            return this;
        }

        public BasicReEntrantHandler setThrowOnMax(boolean throwOnMax) {
            this.throwOnMax = throwOnMax;
            return this;
        }

        @ServiceRegistered
        public void schedulerRegistered(SchedulerService schedulerService, String name) {
            this.schedulerService = schedulerService;
            System.out.println("SchedulerService registered in BasicReEntrantHandler");
        }

        @Override
        public void start() {
            System.out.println("BasicReEntrantHandler started, beginning re-entrant publishing...");
            publishReEntrantEvent();
        }

        private void publishReEntrantEvent() {
            // 1) Publish a new event into the processing cycle
            String eventMessage = "Re-Entrant Event [" + count + "]";
            getContext().processAsNewEventCycle(eventMessage);
            count++;

            System.out.println("Published: " + eventMessage);

            // 2) Check termination condition
            if (count >= maxCount) {
                if (throwOnMax) {
                    throw new RuntimeException("Reached maxCount=" + maxCount);
                }
                System.out.println("Reached maxCount=" + maxCount + ", stopping re-entrant publishing");
                return; // stop scheduling further events
            }

            // 3) Schedule the next callback that will again re-enter the cycle
            schedulerService.scheduleAfterDelay(republishWaitMillis, this::publishReEntrantEvent);
        }

        @Override
        protected boolean handleEvent(Object event) {
            // Event is observed as if it came from outside
            System.out.println("BasicReEntrantHandler received: " + event);
            return true;
        }
    }

    /**
     * Handler that creates a countdown sequence using re-entrant publishing
     */
    @Getter
    public static class CountdownHandler extends ObjectEventHandlerNode {
        private SchedulerService schedulerService;
        private int currentValue;
        private int startValue = 10;
        private long delayMillis = 200;

        // Fluent setters
        public CountdownHandler setStartValue(int startValue) {
            this.startValue = startValue;
            return this;
        }

        public CountdownHandler setDelayMillis(long delayMillis) {
            this.delayMillis = delayMillis;
            return this;
        }

        @ServiceRegistered
        public void schedulerRegistered(SchedulerService schedulerService, String name) {
            this.schedulerService = schedulerService;
        }

        @Override
        public void start() {
            currentValue = startValue;
            publishCountdownEvent();
        }

        private void publishCountdownEvent() {
            String eventMessage = "Countdown: " + currentValue;
            getContext().processAsNewEventCycle(eventMessage);

            currentValue--;

            if (currentValue >= 0) {
                // Schedule next countdown event
                schedulerService.scheduleAfterDelay(delayMillis, this::publishCountdownEvent);
            } else {
                // Countdown finished, publish final event
                getContext().processAsNewEventCycle("Countdown: BLAST OFF! 🚀");
            }
        }

        @Override
        protected boolean handleEvent(Object event) {
            System.out.println("CountdownHandler: " + event);
            return true;
        }
    }

    /**
     * Handler that publishes events in bursts
     */
    @Getter
    public static class BurstPublisher extends ObjectEventHandlerNode {
        private SchedulerService schedulerService;
        private int burstSize = 5;
        private int burstCount = 3;
        private long burstDelayMillis = 500;
        private long eventDelayMillis = 100;

        private int currentBurst = 0;
        private int currentEventInBurst = 0;

        // Fluent setters
        public BurstPublisher setBurstSize(int burstSize) {
            this.burstSize = burstSize;
            return this;
        }

        public BurstPublisher setBurstCount(int burstCount) {
            this.burstCount = burstCount;
            return this;
        }

        public BurstPublisher setBurstDelayMillis(long burstDelayMillis) {
            this.burstDelayMillis = burstDelayMillis;
            return this;
        }

        public BurstPublisher setEventDelayMillis(long eventDelayMillis) {
            this.eventDelayMillis = eventDelayMillis;
            return this;
        }

        @ServiceRegistered
        public void schedulerRegistered(SchedulerService schedulerService, String name) {
            this.schedulerService = schedulerService;
        }

        @Override
        public void start() {
            currentBurst = 0;
            publishNextBurst();
        }

        private void publishNextBurst() {
            if (currentBurst >= burstCount) {
                System.out.println("All bursts completed!");
                return;
            }

            currentBurst++;
            currentEventInBurst = 0;
            System.out.println("Starting burst " + currentBurst + " of " + burstCount);
            publishNextEventInBurst();
        }

        private void publishNextEventInBurst() {
            currentEventInBurst++;
            String eventMessage = "Burst " + currentBurst + " Event " + currentEventInBurst;
            getContext().processAsNewEventCycle(eventMessage);

            if (currentEventInBurst < burstSize) {
                // Schedule next event in current burst
                schedulerService.scheduleAfterDelay(eventDelayMillis, this::publishNextEventInBurst);
            } else {
                // Current burst complete, schedule next burst
                schedulerService.scheduleAfterDelay(burstDelayMillis, this::publishNextBurst);
            }
        }

        @Override
        protected boolean handleEvent(Object event) {
            System.out.println("BurstPublisher: " + event);
            return true;
        }
    }

    /**
     * Mock SchedulerService for demonstration purposes
     */
    public static class MockSchedulerService implements SchedulerService {
        @Override
        public long scheduleAtTime(long expireTimeMillis, Runnable expiryAction) {
            // Simple implementation using a separate thread
            new Thread(() -> {
                try {
                    long delay = expireTimeMillis - System.currentTimeMillis();
                    if (delay > 0) {
                        Thread.sleep(delay);
                    }
                    expiryAction.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            return System.currentTimeMillis();
        }

        @Override
        public long scheduleAfterDelay(long waitTimeMillis, Runnable expiryAction) {
            return scheduleAtTime(System.currentTimeMillis() + waitTimeMillis, expiryAction);
        }

        @Override
        public long milliTime() {
            return System.currentTimeMillis();
        }

        @Override
        public long microTime() {
            return System.currentTimeMillis() * 1000;
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }
    }

    /**
     * Utility class for creating different re-entrant patterns
     */
    public static class ReEntrantPatterns {

        public static BasicReEntrantHandler createPeriodicPublisher(int maxEvents, long intervalMs) {
            return new BasicReEntrantHandler()
                    .setMaxCount(maxEvents)
                    .setRepublishWaitMillis(intervalMs)
                    .setThrowOnMax(false);
        }

        public static CountdownHandler createCountdown(int startValue, long delayMs) {
            return new CountdownHandler()
                    .setStartValue(startValue)
                    .setDelayMillis(delayMs);
        }

        public static BurstPublisher createBurstPattern(int burstSize, int burstCount, long burstDelay, long eventDelay) {
            return new BurstPublisher()
                    .setBurstSize(burstSize)
                    .setBurstCount(burstCount)
                    .setBurstDelayMillis(burstDelay)
                    .setEventDelayMillis(eventDelay);
        }
    }
}
