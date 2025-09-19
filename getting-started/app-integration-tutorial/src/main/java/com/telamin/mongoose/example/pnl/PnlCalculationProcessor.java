package com.telamin.mongoose.example.pnl;

import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.runtime.EventProcessor;
import com.telamin.mongoose.example.pnl.calculator.PnlSummaryCalc;
import com.telamin.mongoose.example.pnl.calculator.TradeFilter;
import com.telamin.mongoose.example.pnl.calculator.TradeLegToPositionAggregate;
import com.telamin.mongoose.example.pnl.events.Trade;
import com.telamin.mongoose.example.pnl.events.TradeLeg;

import java.util.function.Supplier;

import static com.telamin.mongoose.example.pnl.server.PnlExampleMain.EOB_TRADE_KEY;

public class PnlCalculationProcessor implements Supplier<EventProcessor<?>> {

    @Override
    public EventProcessor<?> get() {
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
        return processor;
    }
}
