package com.telamin.mongoose.example.pnl.helper;

import com.fluxtion.agrona.concurrent.SnowflakeIdGenerator;
import com.telamin.mongoose.example.pnl.events.Trade;
import com.telamin.mongoose.example.pnl.events.MidPrice;
import com.telamin.mongoose.example.pnl.refdata.Instrument;
import com.telamin.mongoose.example.pnl.refdata.Symbol;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Random;

@RequiredArgsConstructor
public class RandomTradeGenerator {
    private final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(0);
    private final Random random = new Random();
    private final List<Symbol> symbols = List.of(
            new Symbol("EURJPY", new Instrument("EUR"), new Instrument("JPY")),
            new Symbol("USDJPY", new Instrument("USD"), new Instrument("JPY")),
            new Symbol("EURUSD", new Instrument("EUR"), new Instrument("USD")),
            new Symbol("USDCHF", new Instrument("USD"), new Instrument("CHF")),
            new Symbol("EURGBP", new Instrument("EUR"), new Instrument("GBP")),
            new Symbol("GBPUSD", new Instrument("GBP"), new Instrument("USD"))
    );

    public Trade generateRandomTrade() {
        Symbol randomSymbol = symbols.get(random.nextInt(symbols.size()));

        double dealtVolume = Math.round(random.nextDouble(-2000, 2000) * 100.0) / 100.0;
        double rate = random.nextDouble(0.5, 2.0);
        double contraVolume = Math.round(-dealtVolume * rate * 100.0) / 100.0;

        return new Trade(randomSymbol, idGenerator.nextId(), dealtVolume, contraVolume);
    }

    public MidPrice generateRandomMidPrice() {
        Symbol randomSymbol = symbols.get(random.nextInt(symbols.size()));
        double midRate = Math.round(random.nextDouble(0.5, 2.0) * 10000.0) / 10000.0;
        return new MidPrice(randomSymbol, midRate);
    }
}
