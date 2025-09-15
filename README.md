# Mongoose Examples

[![CI](https://github.com/telaminai/mongoose-examples/actions/workflows/ci.yml/badge.svg)](https://github.com/telaminai/mongoose-examples/actions/workflows/ci.yml)

This repository provides tutorials, examples, and case studies for [Mongoose](https://github.com/telaminai/mongoose), a high-performance event processing framework for Java.

## Purpose

The examples in this repository demonstrate various aspects of Mongoose:

- How to configure and boot a Mongoose server
- Different approaches to event processing
- Integration with various input and output sources
- Performance optimization techniques
- Testing strategies for Mongoose applications

These examples range from simple "getting started" tutorials to more complex case studies showing real-world usage patterns.

## Available Examples

### Getting Started

- [Five Minute Tutorial](gettting-started/five-minute-tutorial) - A programmatic approach to configuring a Mongoose server with multiple named event feeds and selective event processing.
- [Five Minute YAML Tutorial](gettting-started/five-minute-yaml-tutorial) - The same functionality as the Five Minute Tutorial, but using YAML configuration instead of programmatic configuration.

### Plugins

- [Event Source Example](plugins/event-source-example) - Demonstrates how to create a custom event source by extending the AbstractAgentHostedEventSourceService class, allowing you to generate events at regular intervals.
- [Event Source Non-Agent Example](plugins/event-source-nonagent-example) - Shows how to create a custom event source that manages its own threading using a ScheduledExecutorService instead of relying on the Mongoose agent infrastructure.
- [Message Sink Example](plugins/message-sink-example) - Illustrates how to create a custom message sink by extending the AbstractMessageSink class, with configurable formatting options for console output.
- [Service Plugin Example](plugins/service-plugin-example) - Demonstrates how to create custom service plugins that can be registered with Mongoose server, including both simple lifecycle services and worker services that run background tasks.

### How-To Guides

- [Subscribing to Named Event Feeds](how-to/subscribing-to-named-event-feeds) - Shows how to subscribe to specific named EventFeeds and ignore others, demonstrating selective event processing.
- [Data Mapping](how-to/data-mapping) - Demonstrates how to transform incoming feed events to a different type using value mapping with Function<Input, ?> mappers.
- [Using the Scheduler Service](how-to/using-the-scheduler-service) - Shows how to use the built-in SchedulerService for delayed actions, periodic jobs, and scheduled triggers.
- [Injecting Config into a Processor](how-to/injecting-config-into-a-processor) - Demonstrates how to inject configuration data into event processors for customizable behavior.
- [Handler Pipe](how-to/handler-pipe) - Shows how to use HandlerPipe for in-VM communication between handlers, enabling message passing patterns.
- [Object Pool](how-to/object-pool) - Demonstrates zero-GC object pooling techniques for high-performance event processing with minimal garbage collection.
- [Replay](how-to/replay) - Shows how to implement deterministic replay with ReplayRecord and the data-driven clock for testing and debugging.
- [Core Pin](how-to/core-pin) - Shows how to pin agent threads to specific CPU cores for optimal performance in latency-sensitive applications.
- [Writing a Custom Event to Invoke Strategy](how-to/writing-a-custom-event-to-invoke-strategy) - Demonstrates how to create custom EventToInvokeStrategy implementations for specialized event handling patterns.

## Prerequisites

- Java 21+
- Maven 3.8+
- Access to the com.telamin:mongoose dependency (installed locally or available in your Maven repositories)

## Building the Examples

You can build all examples at once from the root directory:

```bash
./mvnw clean install
```

Or build a specific example:

```bash
./mvnw -pl gettting-started/five-minute-tutorial clean install
```

## Running the Examples

Each example has its own README with specific instructions for running it. Generally, you can run the examples:

1. Via your IDE by setting the appropriate main class
2. Via the command line using the generated JAR files

## Contributing

Contributions to this repository are welcome! If you have an example or tutorial that demonstrates a useful aspect of Mongoose, please consider submitting a pull request.

## Links

- [Mongoose GitHub Repository](https://github.com/telaminai/mongoose)
- [Mongoose Project Homepage](https://telaminai.github.io/mongoose/)
- [Mongoose Documentation](https://telaminai.github.io/mongoose/docs/)

## Under construction 
### How-To Guides
- [Scheduler processAsNewEventCycle](how-to/scheduler-processAsNewEventCycle) - Demonstrates re-entrant publishing with processAsNewEventCycle and SchedulerService.
