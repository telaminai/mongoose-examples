package com.telamin.mongoose.example.pnl.helper;

import java.util.function.Function;

public class MapToJson implements Function<Object, String> {

    @Override
    public String apply(Object o) {
        return DataMappers.toJson(o);
    }
}
