package com.telamin.mongoose.example.pnl.helper;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Function;

public class MapFromJson implements Function<String, Object> {

    @Getter
    @Setter
    private String targetType;
    private Class<?> targetClass;

    @Override
    public Object apply(String o) {
        if (targetClass == null) {
            try {
                targetClass = Class.forName(targetType);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to load class " + targetType, e);
            }
        }
        return DataMappers.toObject(o, targetClass);
    }
}
