package com.telamin.mongoose.example.spring.loader;

import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;

/**
 * Event handler instantiated from log-processor.xml by svc-loader-spring.
 *
 * <p>{@code prefix} is injected via Spring bean-property setter — the XML's
 * {@code <property name="prefix" value="..."/>} ends up here.
 *
 * <p>The processor is dynamically added by svc-loader-spring; it does not get
 * the automatic feed subscriptions that statically-registered processors
 * receive at boot. {@link #start()} therefore explicitly subscribes this
 * handler's processor to the in-memory feed by name.
 */
public class SpringLogHandler extends ObjectEventHandlerNode {

    public static final String FEED_NAME = "springLoader";

    private String prefix = "SPRING-LOADED-HANDLER:";

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void start() {
        getContext().subscribeToNamedFeed(FEED_NAME);
    }

    @Override
    protected boolean handleEvent(Object event) {
        System.out.println(prefix + " " + event);
        return true;
    }
}
