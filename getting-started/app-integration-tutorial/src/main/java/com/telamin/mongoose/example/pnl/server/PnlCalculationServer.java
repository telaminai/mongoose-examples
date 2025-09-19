/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.pnl.server;

import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.file.FileEventSource;
import com.telamin.mongoose.connector.file.FileMessageSink;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.example.pnl.PnlCalculationProcessor;
import com.telamin.mongoose.example.pnl.events.MidPrice;
import com.telamin.mongoose.example.pnl.events.MtmInstrument;
import com.telamin.mongoose.example.pnl.events.Trade;
import com.telamin.mongoose.example.pnl.helper.DataMappers;

import static com.telamin.mongoose.example.pnl.server.PnlExampleMain.*;


public class PnlCalculationServer {
    private static InMemoryEventSource<MtmInstrument> mtmFeed;

    public void startPnlCalculationServer() {
        var mongooseConfigBuilder = MongooseServerConfig.builder();

        buildHandlerLogic(mongooseConfigBuilder);
        buildFeeds(mongooseConfigBuilder);
        buildSinks(mongooseConfigBuilder);
        MongooseServer.bootServer(mongooseConfigBuilder.build());
    }

    private static void buildHandlerLogic(MongooseServerConfig.Builder mongooseConfigBuilder) {
//        PnlSummaryCalc pnlSummaryCalc = new PnlSummaryCalc();
//        TradeFilter tradeFilter = new TradeFilter();
//
//        EventProcessor<?> processor = (EventProcessor) DataFlow.subscribe(Trade.class)
//                .flatMapFromArray(Trade::tradeLegs, EOB_TRADE_KEY)
//                .groupBy(TradeLeg::instrument, TradeLegToPositionAggregate::new)
//                .publishTriggerOverride(pnlSummaryCalc)
//                .map(pnlSummaryCalc::calcMtmAndUpdateSummary)
//                .filter(tradeFilter::publishPnlResult)
//                .sink("pnl-sink")
//                .build();

        EventProcessorConfig<EventProcessor<?>> eventProcessorConfig = EventProcessorConfig.builder()
                .name("pnl-processor")
                .handlerBuilder(new PnlCalculationProcessor())
//                .handler(processor)
                .build();

        var threadConfig = ThreadConfig.builder()
                .agentName("pnl-agent")
                .idleStrategy(new SleepingMillisIdleStrategy(1))
                .build();

        mongooseConfigBuilder.addProcessor("pnl-agent", eventProcessorConfig);
        mongooseConfigBuilder.addThread(threadConfig);
    }

    private static void buildFeeds(MongooseServerConfig.Builder mongooseServerConfig) {
        FileEventSource priceFeed = new FileEventSource();
        priceFeed.setFilename(INPUT_MID_RATE_JSONL);
        priceFeed.setReadStrategy(ReadStrategy.EARLIEST);
        EventFeedConfig<?> pricesFeedConfig = EventFeedConfig.<String>builder()
                .instance(priceFeed)
                .valueMapper(row -> DataMappers.toObject(row, MidPrice.class))
                .broadcast(true)
                .name("prices")
                .agent("feeds-agent", new SleepingMillisIdleStrategy())
                .build();

        FileEventSource tradesFeed = new FileEventSource();
        tradesFeed.setFilename(INPUT_TRADES_JSONL);
        tradesFeed.setReadStrategy(ReadStrategy.EARLIEST);

        EventFeedConfig<?> tradesFeedConfig = EventFeedConfig.<String>builder()
                .instance(tradesFeed)
                .valueMapper(row -> DataMappers.toObject(row, Trade.class))
                .broadcast(true)
                .name("trades")
                .agent("feeds-agent", new SleepingMillisIdleStrategy())
                .build();

        mtmFeed = new InMemoryEventSource<>();
        EventFeedConfig<?> mtmFeedConfig = EventFeedConfig.builder()
                .instance(mtmFeed)
                .broadcast(true)
                .name("mtmFeed")
                .agent("feeds-agent", new SleepingMillisIdleStrategy())
                .build();

        mongooseServerConfig
                .addEventFeed(pricesFeedConfig)
                .addEventFeed(tradesFeedConfig)
                .addEventFeed(mtmFeedConfig);
    }

    private static void buildSinks(MongooseServerConfig.Builder mongooseServerConfig) {
        FileMessageSink fileSink = new FileMessageSink();
        fileSink.setFilename(OUTPUT_PNL_SUMMARY_JSONL);

        EventSinkConfig<MessageSink<?>> sinkConfig = EventSinkConfig.<MessageSink<?>>builder()
                .instance(fileSink)
                .valueMapper(DataMappers::toJson)
                .name("pnl-sink")
                .build();

        mongooseServerConfig.addEventSink(sinkConfig);
    }

}