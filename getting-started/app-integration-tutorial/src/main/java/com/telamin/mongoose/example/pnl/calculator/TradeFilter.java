package com.telamin.mongoose.example.pnl.calculator;

import com.fluxtion.agrona.IoUtil;
import com.telamin.fluxtion.runtime.annotations.Initialise;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.mongoose.example.pnl.events.Trade;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.nio.MappedByteBuffer;

public class TradeFilter {

    private boolean newTrade = false;
    private MappedByteBuffer commitPointer;
    private long lastTradeId;
    @Setter
    @Getter
    private String pointerFileName = "./data-in/tradesIn.readPointer";

    @Initialise
    public void initialise() {
        File committedReadFile = new File(pointerFileName);
        lastTradeId = 0;
        if (committedReadFile.exists()) {
            commitPointer = IoUtil.mapExistingFile(committedReadFile, "committedReadFile_TradesIn");
            lastTradeId = commitPointer.getLong(0);
        } else {
            commitPointer = IoUtil.mapNewFile(committedReadFile, 1024);
        }
        System.out.println("starting trade filter with lastTradeId: " + lastTradeId);
    }

    @OnEventHandler(propagate = false)
    public void isTrade(Trade trade) {
        newTrade = trade.id() > lastTradeId;
        if (newTrade) {
            lastTradeId = trade.id();
            commitPointer.putLong(0, lastTradeId);
        }
    }

    public boolean publishPnlResult() {
        return newTrade;
    }
}
