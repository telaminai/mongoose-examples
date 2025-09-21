package com.telamin.mongoose.example.howto;

import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventProcessorGroupConfig;
import com.telamin.mongoose.config.EventSinkConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;
import com.telamin.mongoose.service.scheduler.SchedulerService;

/**
 * Example demonstrating how to use the SchedulerService in Mongoose server.
 * 
 * This example shows:
 * - Injecting SchedulerService into processors using @ServiceRegistered
 * - One-shot delayed actions using scheduleAfterDelay()
 * - Periodic jobs using the rescheduling pattern
 * - Scheduled triggers using ScheduledTriggerNode
 * - Time helpers (milliTime, microTime, nanoTime)
 */
public class UsingTheSchedulerServiceExample {

    public static void main(String[] args) throws Exception {
        // Create event source
        InMemoryEventSource<String> eventSource = new InMemoryEventSource<>();

        // Configure the event feed
        EventFeedConfig<String> feedConfig = EventFeedConfig
                .<String>builder()
                .instance(eventSource)
                .name("scheduler-feed")
                .build();

        // Create processors that use the scheduler
        SchedulerDemoProcessor processor = new SchedulerDemoProcessor();

        EventProcessorGroupConfig processorGroupConfig = EventProcessorGroupConfig.builder()
                .agentName("scheduler-processor-agent")
                .put("scheduler-processor", new EventProcessorConfig(processor))
                .build();

        // Create sink to collect processed events
        InMemoryMessageSink sink = new InMemoryMessageSink();
        EventSinkConfig<InMemoryMessageSink> sinkConfig = EventSinkConfig
                .<InMemoryMessageSink>builder()
                .instance(sink)
                .name("scheduler-sink")
                .build();

        // Configure and start the server
        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addEventFeed(feedConfig)
                .addProcessorGroup(processorGroupConfig)
                .addEventSink(sinkConfig)
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            System.out.println("Scheduler Service Example Started");
            System.out.println("Demonstrating various scheduler patterns...");

            // Publish an initial event to trigger scheduler demonstrations
            eventSource.publishNow("start-demo");

            // Let the example run for a while to see scheduled actions
            Thread.sleep(8000);

            // Stop periodic jobs
            processor.stopPeriodicJob();

            Thread.sleep(1000);

        } finally {
            server.stop();
        }

        System.out.println("\nScheduler Service Example Completed");
    }

    /**
     * Processor that demonstrates various scheduler patterns
     */
    public static class SchedulerDemoProcessor extends ObjectEventHandlerNode {
        private SchedulerService scheduler;
        private volatile boolean periodicJobRunning = true;
        private int periodicJobCount = 0;

        @ServiceRegistered
        public void scheduler(SchedulerService scheduler, String name) {
            this.scheduler = scheduler;
            System.out.println("SchedulerService injected into processor");
        }

        @Override
        public void start() {
            getContext().subscribeToNamedFeed("scheduler-feed");
        }

        @Override
        protected boolean handleEvent(Object event) {
            if (event instanceof String message && "start-demo".equals(message)) {
                demonstrateSchedulerPatterns();
            }
            return true;
        }

        private void demonstrateSchedulerPatterns() {
            System.out.println("\n=== Scheduler Patterns Demo ===");

            // 1. One-shot delay
            System.out.println("1. Scheduling one-shot action in 1 second...");
            scheduler.scheduleAfterDelay(1000, () -> {
                System.out.println("   One-shot action executed! Current time: " + scheduler.milliTime());
            });

            // 2. Schedule at specific time
            long runAt = scheduler.milliTime() + 2000; // 2 seconds from now
            System.out.println("2. Scheduling action at specific time: " + runAt);
            scheduler.scheduleAtTime(runAt, () -> {
                System.out.println("   Scheduled time action executed! Time: " + scheduler.milliTime());
            });

            // 3. Start periodic job
            System.out.println("3. Starting periodic job (every 1.5 seconds)...");
            schedulePeriodicJob(1500);

            // 4. Demonstrate time helpers
            System.out.println("4. Time helpers:");
            System.out.println("   milliTime(): " + scheduler.milliTime());
            System.out.println("   microTime(): " + scheduler.microTime());
            System.out.println("   nanoTime(): " + scheduler.nanoTime());
        }

        private void schedulePeriodicJob(long periodMs) {
            scheduler.scheduleAfterDelay(periodMs, () -> {
                if (!periodicJobRunning) {
                    System.out.println("   Periodic job stopped");
                    return;
                }

                periodicJobCount++;
                System.out.println("   Periodic job execution #" + periodicJobCount + 
                                 " at time: " + scheduler.milliTime());

                // Reschedule for next execution
                schedulePeriodicJob(periodMs);
            });
        }

        public void stopPeriodicJob() {
            System.out.println("Stopping periodic job...");
            periodicJobRunning = false;
        }
    }
}
