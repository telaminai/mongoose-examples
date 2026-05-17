/*
 * SPDX-FileCopyrightText: © 2024 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.pnl.server;

import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.example.pnl.events.MtmInstrument;


public class PnlExampleMain {

    public static final String EOB_TRADE_KEY = "eob";
    public static final String INPUT_TRADES_JSONL = "./data-in/trades.jsonl";
    public static final String INPUT_MID_RATE_JSONL = "./data-in/midRate.jsonl";
    public static final String OUTPUT_PNL_SUMMARY_JSONL = "./data-out/pnl-summary.jsonl";

    private static InMemoryEventSource<MtmInstrument> mtmFeed;


    public static void main(String[] args) throws InterruptedException {
        PnlCalculationServer pnlCalculationServer = new PnlCalculationServer();
        pnlCalculationServer.startPnlCalculationServer();

        DataGeneratorServer dataGeneratorServer = new DataGeneratorServer();
        dataGeneratorServer.startFilePublish();
    }
}