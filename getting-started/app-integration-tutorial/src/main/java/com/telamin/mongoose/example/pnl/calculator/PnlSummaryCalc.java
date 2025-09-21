package com.telamin.mongoose.example.pnl.calculator;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.telamin.fluxtion.runtime.event.Signal;
import com.telamin.fluxtion.runtime.flowfunction.groupby.GroupBy;
import com.telamin.mongoose.example.pnl.events.PnlSummary;
import com.telamin.mongoose.example.pnl.server.PnlExampleMain;
import com.telamin.mongoose.example.pnl.refdata.Instrument;
import lombok.Getter;

public class PnlSummaryCalc {

    @Getter
    private final MtMRateCalculator mtMRateCalculator;
    @FluxtionIgnore
    private final PnlSummary pnlSummary = new PnlSummary();

    public PnlSummaryCalc(MtMRateCalculator mtMRateCalculator) {
        this.mtMRateCalculator = mtMRateCalculator;
    }

    public PnlSummaryCalc() {
        this.mtMRateCalculator = new MtMRateCalculator();
    }

    @OnEventHandler(filterString = PnlExampleMain.EOB_TRADE_KEY)
    public boolean eobTrigger(Signal<String> eobSignal) {
        return true;
    }

    public PnlSummary updateSummary(GroupBy<Instrument, InstrumentPosMtm> instrumentMtmGroupBy) {
        pnlSummary.setMtmInstrument(mtMRateCalculator.getMtmInstrument());
        pnlSummary.getMtmAssetMap().clear();
        pnlSummary.getMtmAssetMap().putAll(instrumentMtmGroupBy.toMap());
        if (pnlSummary.calcPnl()) {
            return pnlSummary;
        }
        return null;
    }

    public PnlSummary calcMtmAndUpdateSummary(GroupBy<Instrument, InstrumentPosMtm> instrumentMtmGroupBy) {
        instrumentMtmGroupBy.toMap().values().forEach(mtMRateCalculator::calculateInstrumentPosMtm);
        pnlSummary.setMtmInstrument(mtMRateCalculator.getMtmInstrument());
        pnlSummary.getMtmAssetMap().clear();
        pnlSummary.getMtmAssetMap().putAll(instrumentMtmGroupBy.toMap());
        if (pnlSummary.calcPnl()) {
            return pnlSummary;
        }
        return null;
    }

    @OnTrigger
    public boolean trigger() {
        return true;
    }
}
