# Five Minute Tutorial Example

**Mongoose project homepage:** https://telaminai.github.io/mongoose/

[![CI](https://github.com/telaminai/mongoose-examples/actions/workflows/ci.yml/badge.svg)](https://github.com/telaminai/mongoose-examples/actions/workflows/ci.yml)

This is a Maven project that provides a "Five Minute Tutorial" application showing how to:

- Create multiple named event feeds
- Subscribe to specific named feeds using a filter handler
- Configure and boot a Mongoose Server with multiple feeds and a processor
- Demonstrate selective event processing based on feed names

The example's main class:

- [FiveMinuteTutorial](src/main/java/com/telamin/mongoose/example/fivemin/FiveMinuteTutorial.java)

Mongoose maven dependency:

```xml
<dependencies>
    <dependency>
        <groupId>com.telamin</groupId>
        <artifactId>mongoose</artifactId>
        <version>${mongoose.version}</version>
    </dependency>
</dependencies>
```

## What it demonstrates

- Creating multiple named in-memory event sources
- Implementing a filter handler that only processes events from specific named feeds
- Subscribing to specific named feeds during handler initialization
- Configuring multiple feed agents with their own idle strategies
- Using an in-memory sink to collect and display the filtered messages

## Prerequisites

- Java 21+
- Maven 3.8+
- Access to the com.telamin:mongoose dependency (installed locally or available in your Maven repositories)
    - If you are developing alongside the Mongoose repo, run `mvn -q install` in the Mongoose project first to install
      it to your local repository, and ensure the version in this example's pom.xml (<mongoose.version>) matches.

## Sample code

The sample below shows how to create a filter handler that only processes events from specific named feeds:

```java
public class NamedFeedsFilterHandler extends ObjectEventHandlerNode {

    private final Set<String> acceptedFeedNames;
    private MessageSink<String> sink;

    public NamedFeedsFilterHandler(Set<String> acceptedFeedNames) {
        this.acceptedFeedNames = acceptedFeedNames;
    }

    @ServiceRegistered
    public void wire(MessageSink<String> sink, String name) {
        this.sink = sink;
    }

    @Override
    public void start() {
        acceptedFeedNames.forEach(feedName -> getContext().subscribeToNamedFeed(feedName));
    }

    @Override
    protected boolean handleEvent(Object event) {
        if (sink == null || event == null) {
            return true;
        }
        if (event instanceof String feedName) {
            sink.accept(feedName);
        }
        // continue processing chain
        return true;
    }
}
```

Example source in this project: [FiveMinuteTutorial](src/main/java/com/telamin/mongoose/example/fivemin/FiveMinuteTutorial.java)

The main application sets up three named feeds but only processes events from two of them:

```java
// Feeds: three named in-memory sources
InMemoryEventSource<String> prices = new InMemoryEventSource<>();
InMemoryEventSource<String> orders = new InMemoryEventSource<>();
InMemoryEventSource<String> news = new InMemoryEventSource<>();

// Processor that only forwards events from feeds: prices, news
NamedFeedsFilterHandler filterHandler = new NamedFeedsFilterHandler(Set.of("prices", "news"));

// Build EventFeed configs with names
EventFeedConfig<?> pricesFeed = EventFeedConfig.builder()
        .instance(prices)
        .name("prices")
        .agent("prices-agent", new BusySpinIdleStrategy())
        .build();

// ... similar configuration for orders and news feeds

// sink config
EventSinkConfig<MessageSink<?>> sinkCfg = EventSinkConfig.<MessageSink<?>>builder()
        .instance(memSink)
        .name("memSink")
        .build();

// build the server composing the component configs
MongooseServerConfig mongooseServerConfig = MongooseServerConfig.builder()
        .addProcessorGroup(processorGroup)
        .addEventFeed(pricesFeed)
        .addEventFeed(ordersFeed)
        .addEventFeed(newsFeed)
        .addEventSink(sinkCfg)
        .build();

MongooseServer server = MongooseServer.bootServer(mongooseServerConfig);

// Publish events to all feeds
prices.offer("p1");
prices.offer("p2");
orders.offer("o1"); // ignored by filter
orders.offer("o2"); // ignored by filter
news.offer("n1");
news.offer("n2");

// Only events from "prices" and "news" feeds will be processed
```

How it boots and runs:

- Create and configure components:
    - Create three named in-memory event sources: prices, orders, and news
    - Create a filter handler that only subscribes to "prices" and "news" feeds
    - Configure each feed with its own agent and idle strategy
    - Set up an in-memory sink to collect processed messages
- Boot the server with the configuration
- Publish events to all three feeds
- Only events from the "prices" and "news" feeds are processed and sent to the sink
- The application prints the received messages, which should only include events from the subscribed feeds

## Build

From this project directory:

- Build: `./mvnw -q package`

## Run

There are two common ways to run the example:

1) Via your IDE:

- Set the main class to `com.telamin.mongoose.example.fivemin.FiveMinuteTutorial`

2) Via the JAR:

- Build: `./mvnw -q package`
- Run: `java -jar target/five-minute-tutorial-1.0-SNAPSHOT.jar`

Expected output:

```
received:
p1
p2
n1
n2
```

Note that events from the "orders" feed (o1, o2) are not included in the output because the filter handler only subscribes to the "prices" and "news" feeds.

## Testing

This project uses JUnit 5 for testing the filter handler:

- Test source: [NamedFeedsFilterHandlerTest](src/test/java/com/telamin/mongoose/example/fivemin/NamedFeedsFilterHandlerTest.java)
- Run all tests: `./mvnw -q test`

```java
@Test
public void testHandleEvent_ForwardsStringEvents() {
    // When
    handler.handleEvent("testEvent");

    // Then
    Assertions.assertEquals(1, testSink.getMessages().size());
    Assertions.assertEquals("testEvent", testSink.getMessages().get(0));
}

@Test
public void testHandleEvent_IgnoresNonStringEvents() {
    // When
    handler.handleEvent(123);

    // Then
    Assertions.assertTrue(testSink.getMessages().isEmpty());
}
```

What the tests validate:

- The filter handler correctly forwards String events to the message sink
- The filter handler ignores non-String events
- The tests use a simple TestMessageSink implementation for capturing and verifying messages

## Notes

- This example demonstrates how to selectively process events based on feed names, which is useful for building systems that need to handle different types of events from different sources.
- Each feed has its own agent thread and idle strategy, allowing for independent configuration of how each feed processes events.
- The filter handler subscribes to specific named feeds during its start() method, showing how to dynamically configure subscriptions.
- The example uses BusySpinIdleStrategy for very low latency. For general usage, consider a less CPU-intensive idle strategy.

## Links

- Mongoose GitHub repository: https://github.com/telaminai/mongoose
- Mongoose project homepage: https://telaminai.github.io/mongoose/
- Example source in this project: [FiveMinuteTutorial](src/main/java/com/telamin/mongoose/example/fivemin/FiveMinuteTutorial.java)