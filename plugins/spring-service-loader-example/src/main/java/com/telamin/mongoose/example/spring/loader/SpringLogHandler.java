package com.telamin.mongoose.example.spring.loader;

import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;

/**
 * Event handler instantiated from log-processor.xml by svc-loader-spring.
 *
 * <p>{@code prefix} is injected via Spring bean-property setter — the XML's
 * {@code <property name="prefix" value="..."/>} ends up here.
 */
public class SpringLogHandler extends ObjectEventHandlerNode {

    public static final String FEED_NAME = "springLoader";

    private String prefix = "SPRING-LOADED-HANDLER:";

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    protected boolean handleEvent(Object event) {
        System.out.println(prefix + " " + event);
        return true;
    }
}
