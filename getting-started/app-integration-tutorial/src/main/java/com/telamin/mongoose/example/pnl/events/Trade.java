/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.pnl.events;


import com.telamin.mongoose.example.pnl.refdata.Instrument;
import com.telamin.mongoose.example.pnl.refdata.Symbol;

public record Trade(Symbol symbol, double dealtVolume, double contraVolume) {

    public Instrument dealtInstrument() {
        return symbol.dealtInstrument();
    }

    public Instrument contraInstrument() {
        return symbol.contraInstrument();
    }

    public TradeLeg[] tradeLegs() {
        return new TradeLeg[]{new TradeLeg(dealtInstrument(), dealtVolume), new TradeLeg(contraInstrument(), contraVolume)};
    }
}
