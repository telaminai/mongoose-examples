package com.telamin.mongoose.example.howto;

import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.service.scheduler.SchedulerService;
import lombok.Getter;

/**
 * Example demonstrating re-entrant publishing with processAsNewEventCycle and SchedulerService.
 * <p>
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

        // ---------- Demonstrate basic re-entrant publishing ----------
        BasicReEntrantHandler basicReEntrantHandler = new BasicReEntrantHandler()
                .setRepublishWaitMillis(100)
                .setMaxCount(5)
                .setThrowOnMax(false);

        // Configure and start the server using the simpler API
        MongooseServerConfig serverConfig = new MongooseServerConfig();
        serverConfig.addProcessor("handler-agent", basicReEntrantHandler, "basicReEntrantHandler");
        demonstrateBasicReEntrant(serverConfig, basicReEntrantHandler);


        // ---------- Demonstrate countdown sequence ----------
        CountdownHandler countdownHandler = new CountdownHandler()
                .setStartValue(10)
                .setDelayMillis(150);

        // Configure and start the server using the simpler API
        serverConfig = new MongooseServerConfig();
        serverConfig.addProcessor("handler-agent", countdownHandler, "countdownHandler");
        demonstrateCountdownSequence(serverConfig);

        // ---------- Demonstrate burst publishing ----------
        BurstPublisher burstPublisher = new BurstPublisher()
                .setBurstSize(3)
                .setBurstCount(2)
                .setBurstDelayMillis(200)
                .setEventDelayMillis(50);

        // Configure and start the server using the simpler API
        serverConfig = new MongooseServerConfig();
        serverConfig.addProcessor("handler-agent", burstPublisher, "countdownHandler");
        demonstrateBurstPublishing(serverConfig);

        System.out.println("\nScheduler processAsNewEventCycle Example Completed");
    }

    private static void demonstrateBasicReEntrant(
            MongooseServerConfig serverConfig,
            BasicReEntrantHandler basicReEntrantHandler) throws InterruptedException {
        System.out.println("\n=== START Basic Re-Entrant Publishing Demo ===");

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        // Wait for completion
        long timeoutMs = 2_000;
        long start = System.currentTimeMillis();
        while (basicReEntrantHandler.getCount() < basicReEntrantHandler.getMaxCount() && (System.currentTimeMillis() - start) < timeoutMs) {
            Thread.sleep(50);
        }

        System.out.println("Basic re-entrant completed. Final count: " + basicReEntrantHandler.getCount());
        server.stop();
        System.out.println("\n=== END Basic Re-Entrant Publishing Demo ===");
    }

    private static void demonstrateCountdownSequence(MongooseServerConfig serverConfig) throws InterruptedException {
        System.out.println("\n=== START Countdown Sequence Demo ===");

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        // Wait for countdown to complete
        Thread.sleep(2000);
        server.stop();
        System.out.println("\n=== END Countdown Sequence Demo ===");
    }

    private static void demonstrateBurstPublishing(MongooseServerConfig serverConfig) throws InterruptedException {
        System.out.println("\n=== START Burst Publishing Demo ===");

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        // Wait for bursts to complete
        Thread.sleep(1500);
        server.stop();
        System.out.println("\n=== END Burst Publishing Demo ===");
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
}
