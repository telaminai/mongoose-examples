/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.pnl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.runtime.EventProcessor;
import com.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.file.FileEventSource;
import com.telamin.mongoose.connector.file.FileMessageSink;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.example.pnl.calculator.PnlSummaryCalc;
import com.telamin.mongoose.example.pnl.calculator.TradeFilter;
import com.telamin.mongoose.example.pnl.calculator.TradeLegToPositionAggregate;
import com.telamin.mongoose.example.pnl.events.MidPrice;
import com.telamin.mongoose.example.pnl.events.MtmInstrument;
import com.telamin.mongoose.example.pnl.events.Trade;
import com.telamin.mongoose.example.pnl.events.TradeLeg;
import org.checkerframework.checker.units.qual.C;

import java.util.concurrent.TimeUnit;

import static com.telamin.mongoose.example.pnl.refdata.RefData.*;


/**
 * Pnl calculator example that uses flatmap operations, no joins and single groupBy methods to achieve the same result as
 * {@link com.fluxtion.example.cookbook.pnl.joinexample.PnlExampleMain}
 * <p>
 * This results in less memory allocations and an additional event cycle for the flatmap. Fluxtion is very efficient in
 * processing an event cycle, down in the nanosecond range in current hardware, so this is probably a good trade off.
 */
public class PnlExampleMain {

    public static final String EOB_TRADE_KEY = "eob";
    private static InMemoryEventSource<MidPrice> priceFeed;
    private static InMemoryEventSource<MtmInstrument> mtmFeed;


    public static void main(String[] args) throws InterruptedException {
        var mongooseConfigBuilder = MongooseServerConfig.builder();

        buildHandlerLogic(mongooseConfigBuilder);
        buildFeeds(mongooseConfigBuilder);
        buildSinks(mongooseConfigBuilder);

        MongooseServer.bootServer(mongooseConfigBuilder.build());
    }

    private static void buildHandlerLogic(MongooseServerConfig.Builder mongooseConfigBuilder){
        PnlSummaryCalc pnlSummaryCalc = new PnlSummaryCalc();
        TradeFilter tradeFilter = new TradeFilter();

        EventProcessor<?> processor = (EventProcessor) DataFlow.subscribe(Trade.class)
                .flatMapFromArray(Trade::tradeLegs, EOB_TRADE_KEY)
                .groupBy(TradeLeg::instrument, TradeLegToPositionAggregate::new)
                .publishTriggerOverride(pnlSummaryCalc)
                .map(pnlSummaryCalc::calcMtmAndUpdateSummary)
                .filter(tradeFilter::publishPnlResult)
                .sink("pnl-sink")
                .build();

        EventProcessorConfig<EventProcessor<?>> eventProcessorConfig = EventProcessorConfig.builder()
                .name("filter-processor")
                .handler(processor)
                .build();

        mongooseConfigBuilder.addProcessor("pnl-agent", eventProcessorConfig);
    }

    private static void buildFeeds(MongooseServerConfig.Builder mongooseServerConfig) {
        FileEventSource priceFeed = new FileEventSource();
        priceFeed.setFilename("./input/midRate.jsonl");
        priceFeed.setReadStrategy(ReadStrategy.EARLIEST);
        priceFeed.setCacheEventLog(true);
        EventFeedConfig<?> pricesFeedConfig = EventFeedConfig.<String>builder()
                .instance(priceFeed)
                .valueMapper(row -> DataMappers.toObject(row, MidPrice.class))
                .broadcast(true)
                .name("prices")
                .agent("feeds-agent", new SleepingMillisIdleStrategy())
                .build();

        FileEventSource tradesFeed = new FileEventSource();
        tradesFeed.setFilename("./input/trades.jsonl");
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
        fileSink.setFilename("./output/pnl-summary.jsonl");

        EventSinkConfig<MessageSink<?>> sinkConfig = EventSinkConfig.<MessageSink<?>>builder()
                .instance(fileSink)
                .valueMapper(DataMappers::toJson)
                .name("pnl-sink")
                .build();

        mongooseServerConfig.addEventSink(sinkConfig);
    }

}