package com.telamin.mongoose.example.pnl;


import com.telamin.fluxtion.runtime.annotations.Start;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.example.pnl.events.MidPrice;
import com.telamin.mongoose.example.pnl.events.Trade;
import com.telamin.mongoose.example.pnl.helper.RandomTradeGenerator;
import com.telamin.mongoose.service.scheduler.SchedulerService;
import lombok.Getter;
import lombok.Setter;

public class DataGeneratorProcessor extends ObjectEventHandlerNode {

    private SchedulerService schedulerService;
    private RandomTradeGenerator randomTradeGenerator = new RandomTradeGenerator();
    private MessageSink<Trade> tradeSink;
    private MessageSink<MidPrice> midPriceSink;
    @Getter
    @Setter
    private long pricePublishSleep = 1_000;
    @Getter
    @Setter
    private long tradePublishSleep = 1_000;

    @ServiceRegistered
    public void sink(MessageSink<?> sink, String name) {
        System.out.println("DataGeneratorProcessor: message sink injected '" + name + "'");
        switch (name) {
            case "trades-sink" -> this.tradeSink = (MessageSink<Trade>) sink;
            case "midPrice-sink" -> this.midPriceSink = (MessageSink<MidPrice>) sink;
        }
    }

    @ServiceRegistered
    public void scheduler(SchedulerService schedulerService, String name) {
        System.out.println("DataGeneratorProcessor: scheduler injected into data generator '" + name + "'");
        this.schedulerService = schedulerService;
    }

    @Start
    public void start() {
        System.out.println("DataGeneratorProcessor: starting");
        schedulerService.scheduleAfterDelay(1000, this::generateTradeData);
        schedulerService.scheduleAfterDelay(1000, this::generateMidPriceData);
    }

    public void generateTradeData() {
        if (tradePublishSleep > 0 && tradeSink != null) {
            Trade trade = randomTradeGenerator.generateRandomTrade();
            tradeSink.accept(trade);
            schedulerService.scheduleAfterDelay(tradePublishSleep, this::generateTradeData);
        }
    }

    public void generateMidPriceData() {
        if (pricePublishSleep > 0 && midPriceSink != null) {
            MidPrice midPrice = randomTradeGenerator.generateRandomMidPrice();
            midPriceSink.accept(midPrice);
            schedulerService.scheduleAfterDelay(pricePublishSleep, this::generateMidPriceData);
        }
    }
}
