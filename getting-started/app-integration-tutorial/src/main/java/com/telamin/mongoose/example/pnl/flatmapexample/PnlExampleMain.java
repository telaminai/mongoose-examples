/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.pnl.flatmapexample;

import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.runtime.EventProcessor;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.example.pnl.calculator.PnlSummaryCalc;
import com.telamin.mongoose.example.pnl.calculator.TradeLegToPositionAggregate;
import com.telamin.mongoose.example.pnl.events.MidPrice;
import com.telamin.mongoose.example.pnl.events.MtmInstrument;
import com.telamin.mongoose.example.pnl.events.Trade;
import com.telamin.mongoose.example.pnl.events.TradeLeg;

import java.util.concurrent.TimeUnit;

import static com.telamin.mongoose.example.pnl.refdata.RefData.*;


/**
 * Pnl calculator example that uses flatmap operations, no joins and single groupBy methods to achieve the same result as
 * {@link com.fluxtion.example.cookbook.pnl.joinexample.PnlExampleMain}
 *
 * This results in less memory allocations and an additional event cycle for the flatmap. Fluxtion is very efficient in
 * processing an event cycle, down in the nanosecond range in current hardware, so this is probably a good trade off.
 */
public class PnlExampleMain {

    public static final String EOB_TRADE_KEY = "eob";
    private static InMemoryEventSource<MidPrice> priceFeed;
    private static InMemoryEventSource<Trade> tradesFeed;
    private static InMemoryEventSource<MtmInstrument> mtmFeed;

    public static void main(String[] args) throws InterruptedException {

        var mongooseConfigBuilder = MongooseServerConfig.builder();

        PnlSummaryCalc pnlSummaryCalc = new PnlSummaryCalc();
        EventProcessor<?> processor = (EventProcessor)DataFlow.subscribe(Trade.class)
                .flatMapFromArray(Trade::tradeLegs, EOB_TRADE_KEY)
                .groupBy(TradeLeg::instrument, TradeLegToPositionAggregate::new)
                .publishTriggerOverride(pnlSummaryCalc)
                .map(pnlSummaryCalc::calcMtmAndUpdateSummary)
                .console()
                .build();

        EventProcessorConfig<EventProcessor<?>> eventProcessorConfig = EventProcessorConfig.builder()
                .name("filter-processor")
                .handler(processor)
                .build();

        mongooseConfigBuilder.addProcessor("pnl-agent", eventProcessorConfig);
        buildFeeds(mongooseConfigBuilder);

        MongooseServer.bootServer(mongooseConfigBuilder.build());

        sendData();
    }

    private static void sendData() throws InterruptedException {
        //send some events
        tradesFeed.offer(new Trade(symbolEURJPY, -400, 80000));
        tradesFeed.offer(new Trade(symbolEURUSD, 500, -1100));
        tradesFeed.offer(new Trade(symbolUSDCHF, 500, -1100));
        tradesFeed.offer(new Trade(symbolEURGBP, 1200, -1000));
        tradesFeed.offer(new Trade(symbolGBPUSD, 1500, -700));

        priceFeed.offer(new MidPrice(symbolEURGBP, 0.9));
        priceFeed.offer(new MidPrice(symbolEURUSD, 1.1));
        priceFeed.offer(new MidPrice(symbolEURCHF, 1.2));

        priceFeed.offer(new MidPrice(symbolEURJPY, 200));

        TimeUnit.SECONDS.sleep(1);
        mtmFeed.offer(new MtmInstrument(EUR));
    }

    private static void buildFeeds(MongooseServerConfig.Builder mongooseServerConfig) {
        priceFeed = new InMemoryEventSource<>();
        EventFeedConfig<?> pricesFeedConfig = EventFeedConfig.builder()
                .instance(priceFeed)
                .broadcast(true)
                .name("prices")
                .agent("feeds-agent", new SleepingMillisIdleStrategy())
                .build();

        tradesFeed = new InMemoryEventSource<>();
        EventFeedConfig<?> tradesFeedConfig = EventFeedConfig.builder()
                .instance(tradesFeed)
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
}