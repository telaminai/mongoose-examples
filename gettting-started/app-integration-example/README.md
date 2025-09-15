# Five Minute YAML Tutorial Example

**Mongoose project homepage:** https://telaminai.github.io/mongoose/

[![CI](https://github.com/telaminai/mongoose-examples/actions/workflows/ci.yml/badge.svg)](https://github.com/telaminai/mongoose-examples/actions/workflows/ci.yml)

This is a Maven project that provides a "Five Minute YAML Tutorial" application showing how to:

- Configure a Mongoose Server using YAML instead of programmatic configuration
- Create multiple named file-based event feeds
- Subscribe to specific named feeds using a filter handler
- Process events from file inputs and write results to file outputs
- Run a long-running application that continuously monitors input files

This example can be compared with the [Five Minute Tutorial](../five-minute-tutorial) which uses a programmatic approach to configuring the server.

## Flow Diagram

The following diagram illustrates the flow of events through the system:

```mermaid
flowchart TD
    %% Event Sources (Files)
    newsFile["File: news-events"]
    priceFile["File: price-events"]
    orderFile["File: order-events"]

    %% Event Sources
    newsFeed["FileEventSource<br>(news-Feed)"]
    priceFeed["FileEventSource<br>(price-Feed)"]
    orderFeed["FileEventSource<br>(order-Feed)"]

    %% Agent
    fileAgent["file-source-agent<br>(Polls files for changes)"]

    %% Handler
    filterHandler["NamedFeedsFilterHandler<br>(Subscribes to: news-Feed, price-Feed)"]

    %% Sink
    fileSink["FileMessageSink<br>(processed-events)"]

    %% Connections
    newsFile --> newsFeed
    priceFile --> priceFeed
    orderFile --> orderFeed

    newsFeed --> fileAgent
    priceFeed --> fileAgent
    orderFeed --> fileAgent

    fileAgent --> filterHandler

    filterHandler --> fileSink

    %% Event flow with subscriptions
    fileAgent -- "news-Feed events" --> filterHandler
    fileAgent -- "price-Feed events" --> filterHandler
    fileAgent -. "order-Feed events<br>(ignored by filter)" .-> filterHandler

    %% Styling
    classDef file fill:#f9f,stroke:#333,stroke-width:2px
    classDef source fill:#f9f,stroke:#333,stroke-width:2px
    classDef agent fill:#bbf,stroke:#333,stroke-width:1px
    classDef handler fill:#bfb,stroke:#333,stroke-width:2px
    classDef sink fill:#fbb,stroke:#333,stroke-width:2px
    classDef event fill:#fffacd,stroke:#333,stroke-width:1px

    class newsFile,priceFile,orderFile file
    class newsFeed,priceFeed,orderFeed source
    class fileAgent agent
    class filterHandler handler
    class fileSink sink
    class n1,n2,n3,p1,p2,p3,o1,o2,o3 event
```

## What it demonstrates

- Configuring a Mongoose Server using YAML configuration
- Using file-based event sources that read from text files
- Using file-based message sinks that write to text files
- Implementing a filter handler that only processes events from specific named feeds
- Running a long-running application that monitors input files for changes

## Prerequisites

- Java 21+
- Maven 3.8+
- Access to the com.telamin:mongoose dependency (installed locally or available in your Maven repositories)
    - If you are developing alongside the Mongoose repo, run `mvn -q install` in the Mongoose project first to install
      it to your local repository, and ensure the version in this example's pom.xml (<mongoose.version>) matches.

## YAML Configuration

The key difference between this example and the [Five Minute Tutorial](../five-minute-tutorial) is the use of YAML configuration instead of programmatic configuration. The YAML configuration is defined in [appConfig.yml](run/appConfig.yml):

```yaml
# --------- EVENT INPUT FEEDS BEGIN CONFIG ---------
eventFeeds:
  - instance: !!com.telamin.mongoose.connector.file.FileEventSource
      filename: data-in/news-events
    name: news-Feed
    agentName: file-source-agent

  - instance: !!com.telamin.mongoose.connector.file.FileEventSource
      filename: data-in/price-events
    name: price-Feed
    agentName: file-source-agent

  - instance: !!com.telamin.mongoose.connector.file.FileEventSource
      filename: data-in/order-events
    name: order-Feed
    agentName: file-source-agent
# --------- EVENT INPUT FEEDS END CONFIG ---------


# --------- EVENT SINKS BEGIN CONFIG -------
eventSinks:
  - instance: !!com.telamin.mongoose.connector.file.FileMessageSink
      filename: data-out/processed-events
    name: fileSink
# --------- EVENT SINKS END CONFIG ---------


# --------- EVENT HANDLERS BEGIN CONFIG ---------
eventHandlers:
  - agentName: processor-agent
    eventHandlers:
      example-processor:
        customHandler: !!com.telamin.mongoose.example.fivemin.NamedFeedsFilterHandler
          acceptedFeedNames: ["news-Feed", "price-Feed"]
# --------- EVENT HANDLERS END CONFIG ---------
```

The YAML configuration defines:

1. **Event Feeds**: Three file-based event sources that read from text files in the `data-in` directory
2. **Event Sinks**: A file-based message sink that writes to a text file in the `data-out` directory
3. **Event Handlers**: A processor that uses the `NamedFeedsFilterHandler` and configures it to accept events from "news-Feed" and "price-Feed" (but not "order-Feed")

## File-Based Input/Output

This example uses file-based event sources and sinks:

### Input Files

The input files are located in the `run/data-in` directory:

- `news-events`: Contains news events, one per line
- `price-events`: Contains price events, one per line
- `order-events`: Contains order events, one per line

Example content of `news-events`:
```
news 1
news 2
news 3
```

The `FileEventSource` runs on a dedicated agent thread (specified by `agentName: file-source-agent` in the YAML configuration) and continuously polls the files for new data. Each time a new line is added to a file, the source reads it and publishes it as an event into the system.

The files have associated read pointers (stored as `.readPointer` files in the same directory), which track the position where the application last read from. This ensures that when the application restarts, old data is not re-read.

### Output Files

The output file is located in the `run/data-out` directory:

- `processed-events`: Contains the processed events from the "news-Feed" and "price-Feed" feeds

The `FileMessageSink` writes each processed event to this file.

## Running the Application

The application is run using the [runMongooseServer.sh](run/runMongooseServer.sh) script:

```bash
cd run
./runMongooseServer.sh
```

The script:

1. Checks if the JAR file exists, and if not, builds it using Maven
2. Creates the `data-out` directory if it doesn't exist
3. Runs the JAR with the system property `mongooseServer.config.file` set to `appConfig.yml`

```bash
java -DmongooseServer.config.file=appConfig.yml -Djava.util.logging.config.file=logging.properties -jar ../target/five-minute-yaml-tutorial-1.0-SNAPSHOT-jar-with-dependencies.jar
```

This is a long-running application, so any additions to the input files will be read and pushed to the handler and the connected sink. To stop the application, press `Ctrl+C`.

### Cleaning Up After a Run

After running the application, you can clean up the generated files using the [cleanup.sh](run/cleanup.sh) script:

```bash
cd run
./cleanup.sh
```

The cleanup script:

1. Deletes all `.readPointer` files in the `data-in` directory, which resets the read positions for all input files
2. Removes all files from the `data-out` directory, clearing any output generated during the run

This is useful when you want to start fresh with a new run of the application, ensuring that all input files will be read from the beginning.

## How it Works

1. The application loads the YAML configuration from `appConfig.yml`
2. It sets up three file-based event sources, each reading from a different input file
3. It configures a filter handler that only subscribes to the "news-Feed" and "price-Feed" feeds
4. It sets up a file-based message sink that writes to the output file
5. When the application starts, it reads the existing content of the input files (if any) and processes it
6. As new content is added to the input files, it is automatically read and processed
7. Only events from the "news-Feed" and "price-Feed" feeds are processed and written to the output file

## Sample Handler Code

The filter handler is similar to the one in the [Five Minute Tutorial](../five-minute-tutorial), but with a key difference: it uses property injection instead of constructor injection to set the accepted feed names:

```java
public class NamedFeedsFilterHandler extends ObjectEventHandlerNode {

    private Set<String> acceptedFeedNames;
    private MessageSink<String> sink;

    public void setAcceptedFeedNames(Set<String> acceptedFeedNames) {
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
            System.out.println("publishing to sink:" + event);
            sink.accept(feedName);
        }
        // continue processing chain
        return true;
    }
}
```

## Notes

- This example demonstrates how to configure a Mongoose Server using YAML instead of programmatic configuration, which can be more maintainable for complex applications.
- The file-based event sources and sinks allow the application to read from and write to text files, making it easy to integrate with other systems.
- The application is long-running and continuously monitors the input files for changes, making it suitable for real-time processing of events.
- The filter handler subscribes to specific named feeds, showing how to selectively process events from different sources.

## Links

- Mongoose GitHub repository: https://github.com/telaminai/mongoose
- Mongoose project homepage: https://telaminai.github.io/mongoose/
- Example source in this project: [NamedFeedsFilterHandler](src/main/java/com/telamin/mongoose/example/fivemin/NamedFeedsFilterHandler.java)
