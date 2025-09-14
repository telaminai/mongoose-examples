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
