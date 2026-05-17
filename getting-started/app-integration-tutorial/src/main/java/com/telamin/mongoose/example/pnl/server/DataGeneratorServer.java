package com.telamin.mongoose.example.pnl.server;

import com.fluxtion.agrona.concurrent.BackoffIdleStrategy;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventSinkConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.ThreadConfig;
import com.telamin.mongoose.connector.file.FileMessageSink;
import com.telamin.mongoose.example.pnl.DataGeneratorProcessor;
import com.telamin.mongoose.example.pnl.helper.DataMappers;

import static com.telamin.mongoose.example.pnl.server.PnlExampleMain.INPUT_MID_RATE_JSONL;
import static com.telamin.mongoose.example.pnl.server.PnlExampleMain.INPUT_TRADES_JSONL;

public class DataGeneratorServer {

    public void startFilePublish() {
        MongooseServerConfig.Builder mongooseServerConfig = MongooseServerConfig.builder();

        // logic
        EventProcessorConfig<?> cfg = new EventProcessorConfig<>();
        cfg.setCustomHandler(new DataGeneratorProcessor());
        cfg.setName("data-gen");

        // trade sink
        FileMessageSink fileSinkTrades = new FileMessageSink();
        fileSinkTrades.setFilename(INPUT_TRADES_JSONL);
        var sinkConfigTrades = EventSinkConfig.builder()
                .instance(fileSinkTrades)
                .valueMapper(DataMappers::toJson)
                .name("trades-sink")
                .build();

        // mid price sink
        FileMessageSink fileSinkMidPrice = new FileMessageSink();
        fileSinkMidPrice.setFilename(INPUT_MID_RATE_JSONL);
        var sinkConfigMidPrice = EventSinkConfig.builder()
                .instance(fileSinkMidPrice)
                .valueMapper(DataMappers::toJson)
                .name("midPrice-sink")
                .build();

        //thread config for the data generator
        var threadConfig = ThreadConfig.builder()
                .agentName("data-gen-agent")
                .idleStrategy(new BackoffIdleStrategy())
                .build();


        mongooseServerConfig.addProcessor("data-gen-agent", cfg);
        mongooseServerConfig.addEventSink(sinkConfigTrades);
        mongooseServerConfig.addEventSink(sinkConfigMidPrice);
        mongooseServerConfig.addThread(threadConfig);
        MongooseServer.bootServer(mongooseServerConfig.build());
    }
}
