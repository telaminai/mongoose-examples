package com.telamin.mongoose.example.howto;

import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventProcessorGroupConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.ReadStrategy;
import com.telamin.mongoose.config.ServiceConfig;
import com.telamin.mongoose.connector.file.FileEventSource;
import com.telamin.mongoose.service.admin.AdminCommandRegistry;
import com.telamin.mongoose.service.admin.AdminCommandRequest;
import com.telamin.mongoose.service.admin.impl.AdminCommandProcessor;
import com.telamin.mongoose.service.admin.impl.CliAdminCommandProcessor;
import com.telamin.mongoose.service.feedstatus.FeedStatusReport;
import com.telamin.mongoose.service.servercontrol.MongooseServerAdmin;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Wait until a feed has caught up and every line has been processed, instead of sleeping for a quiet period.
 * <ul>
 *     <li>from Java: poll {@link MongooseServer#feedStatus(String...)} until {@code settled}, then check
 *     {@code lossless};</li>
 *     <li>from the admin interface: {@code feed.status --json orders} prints the same report;</li>
 *     <li>in the graph: the feed sets {@code feedStatusEvents(true)}, so {@link OrderCounter} receives
 *     {@code FeedStatus} events in order with the lines.</li>
 * </ul>
 * Run with the argument {@code cli} to keep the server running and type {@code feed.status} at the prompt.
 */
public class WaitForCaughtUpExample {

    static final String FEED = "orders";

    public static void main(String[] args) throws Exception {
        boolean cli = List.of(args).contains("cli");
        Path dir = Path.of("target", "wait-for-caught-up-run").toAbsolutePath();
        Files.createDirectories(dir);
        Path orders = dir.resolve("orders.csv");
        Files.deleteIfExists(orders);
        append(orders, 1, 20_000);

        FileEventSource source = new FileEventSource();
        source.setFilename(orders.toString());
        source.setReadStrategy(ReadStrategy.EARLIEST);   // tail the file from its start; no read pointer

        OrderCounter counter = new OrderCounter(FEED, 50);   // ~50 us per line: slower than the file is read

        AdminCommandProcessor admin = new AdminCommandProcessor();
        MongooseServerConfig.Builder builder = MongooseServerConfig.builder()
                .addEventFeed(EventFeedConfig.builder()
                        .instance(source)
                        .name(FEED)
                        .feedStatusEvents(true)                  // also deliver FeedStatus events into the graph
                        .agent("orders-feed-agent", new SleepingMillisIdleStrategy(1))
                        .build())
                .addProcessorGroup(EventProcessorGroupConfig.builder()
                        .agentName("orders-agent")
                        .put("order-counter", new EventProcessorConfig(counter))
                        .build())
                // the admin registry, and the server commands (feed.status is one of them)
                .addService(ServiceConfig.<AdminCommandRegistry>builder()
                        .service(admin).serviceClass(AdminCommandRegistry.class).name("adminService").build())
                .addService(ServiceConfig.builder().service(new MongooseServerAdmin()).name("serverAdmin").build());
        if (cli) {
            builder.addService(ServiceConfig.builder().service(new CliAdminCommandProcessor()).name("adminCli").build());
        }
        MongooseServer server = MongooseServer.bootServer(builder.build());

        long start = System.nanoTime();
        awaitSettled(server, 20_000, 30_000);
        System.out.printf("settled after %d ms with %d lines handled%n",
                (System.nanoTime() - start) / 1_000_000, counter.lines());

        System.out.println("feed.status --json " + FEED + ":");
        System.out.println("  " + adminCommand(admin, "feed.status", "--json", FEED));

        // the file grows: the feed goes BEHIND, then settles again at the new end
        append(orders, 20_001, 25_000);
        // settled is "as of the source's last look": wait for the sequence to cover the new lines as well
        awaitSettled(server, 25_000, 30_000);
        System.out.printf("settled again with %d lines handled%n", counter.lines());
        System.out.println("feed.status " + FEED + ":");
        System.out.println(adminCommand(admin, "feed.status", FEED));

        if (cli) {
            System.out.println("type: feed.status --json " + FEED + "   (Ctrl-C to exit)");
            Thread.currentThread().join();
        }
        server.stop();
    }

    /**
     * The settled rule: every reporting feed CAUGHT_UP (or END_OF_STREAM) and drained. Then check lossless: settled
     * says the run is complete, lossless that nothing was dropped or refused on the way.
     */
    static void awaitSettled(MongooseServer server, long minSequence, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        long lastPrint = 0;
        while (true) {
            FeedStatusReport report = server.feedStatus(FEED);
            FeedStatusReport.Feed feed = report.feed(FEED);
            if (report.settled() && feed.sequence() >= minSequence) {
                if (!report.lossless()) {
                    throw new IllegalStateException("settled, but lossy: " + report);
                }
                if (!report.fallbackFeeds().isEmpty()) {
                    // these feeds cannot report: apply a quiet period to them only
                    System.out.println("feeds without a status: " + report.fallbackFeeds());
                }
                return;
            }
            if (System.currentTimeMillis() - lastPrint > 100) {
                lastPrint = System.currentTimeMillis();
                FeedStatusReport.Subscriber sub = feed.subscribers().isEmpty() ? null : feed.subscribers().get(0);
                System.out.printf("  waiting: %s seq=%d dispatched=%s paused=%s stalled=%s%n", feed.state(),
                        feed.sequence(), sub == null ? "-" : sub.dispatched() + "/" + sub.position(),
                        feed.delivery().paused(), feed.delivery().stalled());
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("not settled after " + timeoutMillis + " ms: " + report);
            }
            Thread.sleep(5);
        }
    }

    /** Run an admin command and return what it printed. */
    static String adminCommand(AdminCommandRegistry admin, String command, String... args) {
        List<String> out = new ArrayList<>();
        AdminCommandRequest request = new AdminCommandRequest();
        request.setCommand(command);
        request.setArguments(List.of(args));
        request.setOutput(o -> out.add(String.valueOf(o)));
        request.setErrOutput(e -> out.add("ERROR: " + e));
        admin.processAdminCommandRequest(request);
        return String.join("\n", out);
    }

    static void append(Path file, int from, int to) throws Exception {
        StringBuilder b = new StringBuilder();
        for (int i = from; i <= to; i++) b.append("order-").append(i).append(",BUY,").append(i % 50 + 1).append('\n');
        Files.writeString(file, b, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
