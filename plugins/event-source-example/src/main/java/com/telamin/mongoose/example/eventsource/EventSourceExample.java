package com.telamin.mongoose.example.eventsource;

import com.fluxtion.agrona.concurrent.SleepingMillisIdleStrategy;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.MongooseServerConfig;

public class EventSourceExample {
    public static void main(String[] args) {
        var handler = new ObjectEventHandlerNode() {
            @Override
            protected boolean handleEvent(Object event) {
                if (event instanceof HeartbeatEvent s) {
                    System.out.println("Event in: " + s);
                }
                return true;
            }};

        // build the handler config
        var eventProcessorConfig = EventProcessorConfig.builder()
                .customHandler(handler)
                .name("heartBeat-handler")
                .build();

        // Build EventFeed configs with names
        EventFeedConfig<?> heartBeatFeed = EventFeedConfig.builder()
                .instance(new HeartBeatEventFeed())
                .name("heartbeatFeed")
                .agent("heartBeatFeed-agent", new SleepingMillisIdleStrategy())
                .broadcast(true)
                .build();

        // compose server from configs
        MongooseServerConfig mongooseServerConfig = MongooseServerConfig.builder()
                .addProcessor("processor-agent", eventProcessorConfig)
                .addEventFeed(heartBeatFeed)
                .build();

        // boot the MongooseServer
        MongooseServer.bootServer(mongooseServerConfig);
    }
}
