package com.telamin.mongoose.example.pnl.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Function;

public interface DataMappers {

    ObjectMapper objectMapper = new ObjectMapper();

    static String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    static <T> T toObject(String charSequence, Class<T> clazz) {
        try {
            return objectMapper.readValue(charSequence, clazz);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

}
