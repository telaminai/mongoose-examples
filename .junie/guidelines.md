# Mongoose Examples Project Guidelines

This document provides guidelines and information for developers working on the Mongoose Examples project.

## Build/Configuration Instructions

### Prerequisites
- Java 21 or higher (project is configured for Java 21)
- Maven 3.6 or higher

### Building the Project
The project uses Maven for build management. To build the entire project:

```bash
# From the project root
mvn clean install
```

To build a specific module:

```bash
# For example, to build the five-minute-tutorial
cd gettting-started/five-minute-tutorial
mvn clean install
```

### Project Structure
The project is organized as a multi-module Maven project:
- `gettting-started/five-minute-tutorial`: Basic example of using Mongoose
- `gettting-started/five-minute-yaml-tutorial`: Example using YAML configuration

## Testing Information

### Running Tests
Tests use JUnit Jupiter (JUnit 5). To run all tests:

```bash
mvn test
```

To run tests in a specific module:

```bash
cd gettting-started/five-minute-tutorial
mvn test
```

To run a specific test class:

```bash
mvn test -Dtest=NamedFeedsFilterHandlerTest
```

### Adding New Tests
1. Create test classes in the `src/test/java` directory, mirroring the package structure of the class being tested
2. Use JUnit Jupiter annotations (`@Test`, `@BeforeEach`, etc.)
3. For mocking dependencies, Mockito is available (add it to the module's pom.xml if not already present)

### Example Test
Here's a simple test for the `NamedFeedsFilterHandler` class:

```java
package com.telamin.mongoose.example.fivemin;

import com.fluxtion.runtime.output.MessageSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NamedFeedsFilterHandlerTest {

    private NamedFeedsFilterHandler handler;
    private TestMessageSink testSink;

    @BeforeEach
    public void setup() {
        testSink = new TestMessageSink();
        
        // Create handler with two accepted feed names
        handler = new NamedFeedsFilterHandler(Set.of("prices", "news"));
        handler.wire(testSink, "testSink");
    }

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
    
    /**
     * Simple implementation of MessageSink for testing
     */
    private static class TestMessageSink implements MessageSink<String> {
        private final List<String> messages = new ArrayList<>();
        
        @Override
        public void accept(String message) {
            messages.add(message);
        }
        
        public List<String> getMessages() {
            return messages;
        }
    }
}
```

## Additional Development Information

### Mongoose Library Usage
The examples demonstrate how to use the Mongoose library:
1. Create event sources (e.g., `InMemoryEventSource`)
2. Configure event feeds with names
3. Create event processors to handle events
4. Configure and boot a Mongoose server

### Key Components
- `EventFeedConfig`: Configures event feeds with names and agents
- `EventProcessorGroupConfig`: Configures processors that handle events
- `EventSinkConfig`: Configures sinks that receive processed events
- `MongooseServerConfig`: Combines feeds, processors, and sinks
- `MongooseServer`: The main server that processes events

### Code Style
- Follow standard Java coding conventions
- Use descriptive names for classes and methods
- Include JavaDoc comments for public classes and methods
- Use the builder pattern for configuration objects

### Debugging
- The `LogRecordListener` can be used to capture and log events
- Use the `InMemoryMessageSink` for testing and debugging
- Set up proper exception handling in event processors