package com.telamin.mongoose.example.streamprog;

import com.fluxtion.agrona.concurrent.BusySpinIdleStrategy;
import com.fluxtion.compiler.builder.dataflow.DataFlow;
import com.fluxtion.runtime.EventProcessor;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;


/**
 * A tutorial demonstrating stream processing capabilities in Mongoose using DataFlow API.
 * Shows how to set up a simple event processing pipeline that subscribes to a named feed,
 * transforms data, and outputs to console.
 */
public class StreamProgrammingTutorial {

    /**
     * Entry point that demonstrates:
     * <ul>
     *   <li>Building a data stream processor with DataFlow API</li>
     *   <li>Configuring an event processor with subscription to "prices" feed</li>
     *   <li>Setting up an in-memory event source</li>
     *   <li>Booting a Mongoose server with the configured components</li>
     *   <li>Publishing test data to the event stream</li>
     * </ul>
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        //build data stream processor subscribing to prices feed, with a simple map function and console output
        EventProcessor<?> processor = (EventProcessor) DataFlow.subscribeToFeed("prices", String.class)
                .map(String::toUpperCase)
                .console("Hello, {}")
                .build();

        EventProcessorConfig<EventProcessor<?>> eventProcessorConfig = EventProcessorConfig.builder()
                .name("filter-processor")
                .handler(processor)
                .build();

        // Build EventFeed configs with name: 'prices'
        InMemoryEventSource<String> prices = new InMemoryEventSource<>();
        EventFeedConfig<?> pricesFeed = EventFeedConfig.builder()
                .instance(prices)
                .name("prices")
                .wrapWithNamedEvent(true)
                .agent("prices-agent", new BusySpinIdleStrategy())
                .build();

        // build server config
        MongooseServerConfig mongooseServerConfig = MongooseServerConfig.builder()
                .addProcessor("processor-agent", eventProcessorConfig)
                .addEventFeed(pricesFeed)
                .build();

        //boot server
        MongooseServer.bootServer(mongooseServerConfig);

        //send some data
        prices.offer("World!!");
    }
}
