package com.telamin.mongoose.example.pnl;

import com.fluxtion.agrona.concurrent.BackoffIdleStrategy;
import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.fluxtion.runtime.annotations.Start;
import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.EventSinkConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.ThreadConfig;
import com.telamin.mongoose.connector.file.FileMessageSink;
import com.telamin.mongoose.example.pnl.events.MidPrice;
import com.telamin.mongoose.example.pnl.events.Trade;
import com.telamin.mongoose.example.pnl.helper.DataMappers;
import com.telamin.mongoose.example.pnl.helper.RandomTradeGenerator;
import com.telamin.mongoose.service.scheduler.SchedulerService;

import static com.telamin.mongoose.example.pnl.PnlExampleMain.INPUT_MID_RATE_JSONL;
import static com.telamin.mongoose.example.pnl.PnlExampleMain.INPUT_TRADES_JSONL;

public class DataGeneratorServer {

    public void startFilePublish() {
        MongooseServerConfig.Builder mongooseServerConfig = MongooseServerConfig.builder();

        // logic
        EventProcessorConfig<?> cfg = new EventProcessorConfig<>();
        cfg.setCustomHandler(new DataGenerator());
        cfg.setName("data-gen");


        // trade sink
        FileMessageSink fileSinkTrades = new FileMessageSink();
        fileSinkTrades.setFilename(INPUT_TRADES_JSONL);
        var sinkConfigTrades = EventSinkConfig.<MessageSink<?>>builder()
                .instance(fileSinkTrades)
                .valueMapper(DataMappers::toJson)
                .name("pnl-sink")
                .build();


        FileMessageSink fileSinkMidPrice = new FileMessageSink();
        fileSinkMidPrice.setFilename(INPUT_MID_RATE_JSONL);
        var sinkConfigMidPrice = EventSinkConfig.<MessageSink<?>>builder()
                .instance(fileSinkMidPrice)
                .valueMapper(DataMappers::toJson)
                .name("midPrice-sink")
                .build();

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


    public static class DataGenerator extends ObjectEventHandlerNode {

        private SchedulerService schedulerService;
        private RandomTradeGenerator randomTradeGenerator = new RandomTradeGenerator();
        private MessageSink<Trade> tradeSink;
        private MessageSink<MidPrice> midPriceSink;

        @ServiceRegistered
        public void sink(MessageSink<?> sink, String name) {
            System.out.println("message sink injected into data generator:" + sink + " " + name);
            switch (name) {
                case "pnl-sink" -> this.tradeSink = (MessageSink<Trade>) sink;
                case "midPrice-sink" -> this.midPriceSink = (MessageSink<MidPrice>) sink;
            }
        }

        @ServiceRegistered
        public void scheduler(SchedulerService schedulerService, String name) {
            this.schedulerService = schedulerService;
            System.out.println("scheduler service injected into data generator");
        }

        @Start
        public void start() {
            System.out.println("starting data generator");
            schedulerService.scheduleAfterDelay(100, this::generateTradeData);
            schedulerService.scheduleAfterDelay(10, this::generateMidPriceData);
        }

        public void generateTradeData() {
            Trade trade = randomTradeGenerator.generateRandomTrade();
            tradeSink.accept(trade);
            schedulerService.scheduleAfterDelay(1, this::generateTradeData);
        }

        public void generateMidPriceData() {
            MidPrice midPrice = randomTradeGenerator.generateRandomMidPrice();
            midPriceSink.accept(midPrice);
            schedulerService.scheduleAfterDelay(1, this::generateMidPriceData);
        }
    }
}
