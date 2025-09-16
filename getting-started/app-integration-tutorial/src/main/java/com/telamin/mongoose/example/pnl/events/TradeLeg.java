package com.telamin.mongoose.example.pnl.events;


import com.telamin.mongoose.example.pnl.refdata.Instrument;

public record TradeLeg(Instrument instrument, double volume) {
}
