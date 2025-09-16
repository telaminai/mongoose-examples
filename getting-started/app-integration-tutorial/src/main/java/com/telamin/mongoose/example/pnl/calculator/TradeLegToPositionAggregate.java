package com.telamin.mongoose.example.pnl.calculator;

import com.fluxtion.runtime.dataflow.aggregate.function.AbstractAggregateFlowFunction;
import com.telamin.mongoose.example.pnl.events.TradeLeg;

public class TradeLegToPositionAggregate extends AbstractAggregateFlowFunction<TradeLeg, InstrumentPosMtm> {

    @Override
    protected InstrumentPosMtm calculateAggregate(TradeLeg tradeLeg, InstrumentPosMtm instrumentPosMtm) {
        instrumentPosMtm = instrumentPosMtm == null ? new InstrumentPosMtm() : instrumentPosMtm;
        instrumentPosMtm.add(tradeLeg);
        return instrumentPosMtm;
    }

    @Override
    protected InstrumentPosMtm resetAction(InstrumentPosMtm instrumentPosMtm) {
        return new InstrumentPosMtm();
    }
}
