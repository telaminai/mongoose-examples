package com.telamin.mongoose.example.howto;

import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventProcessorGroupConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.ReadStrategy;
import com.telamin.mongoose.connector.file.FileEventSource;
import com.telamin.mongoose.service.feedstatus.FeedStatusReport;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.nio.file.Path;

/**
 * The server that {@link AtLeastOnceExample} starts, kills and restarts: a {@code COMMITED} file feed (no overload
 * policy set, so it uses the file-feed default, PAUSE) and a {@link LedgerProcessor}.
 * <p>
 * It runs until the feed has settled (caught up with the file and every line processed), prints the feed status and
 * stops. Arguments: work directory, de-duplicate (true/false), park micros per line.
 */
public final class AtLeastOnceServer {

    static final String FEED = "ledger";

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        boolean deduplicate = Boolean.parseBoolean(args[1]);
        long parkMicros = Long.parseLong(args[2]);

        FileEventSource source = new FileEventSource();
        source.setFilename(dir.resolve("ledger.csv").toString());
        source.setReadStrategy(ReadStrategy.COMMITED);   // resume from ledger.csv.readPointer

        LedgerProcessor ledger = new LedgerProcessor(FEED, dir.resolve("state.txt"), dir.resolve("applied.log"),
                deduplicate, parkMicros);

        MongooseServerConfig config = MongooseServerConfig.builder()
                .addEventFeed(EventFeedConfig.builder()
                        .instance(source)
                        .name(FEED)
                        .agent("ledger-feed-agent", new SleepingMillisIdleStrategy(1))
                        .build())
                .addProcessorGroup(EventProcessorGroupConfig.builder()
                        .agentName("ledger-agent")
                        .put("ledger", new EventProcessorConfig(ledger))
                        .build())
                .build();

        MongooseServer server = MongooseServer.bootServer(config);
        System.out.println("READY");
        while (!server.feedStatus(FEED).settled()) {
            Thread.sleep(5);
        }
        FeedStatusReport report = server.feedStatus(FEED);
        System.out.println("SETTLED " + report.toJson());
        server.stop();
        System.exit(0);
    }
}
