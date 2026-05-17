# YAML Service Loader Example

This example demonstrates how to use the `svc-loader-yaml` plugin to dynamically load event processors into a Mongoose server using YAML configuration files.

## What the Plugin Does

The YAML Service Loader plugin provides a way to:
- Define event processors (nodes, auditors, etc.) in a YAML file.
- Automatically load and boot these processors when the Mongoose server starts.
- Add or update processors at runtime via admin commands.
- Use either the Fluxtion compiler or interpreter to instantiate the processors.

## How the Example Works

1. **`YamlLogHandler`**: A simple event handler class that prints received events to the console with a customizable prefix.
2. **`log-processor.yaml`**: A YAML file that configures an event processor containing an instance of `YamlLogHandler`. It uses the `!!` YAML tag to specify the Java class to instantiate and sets the `prefix` property.
3. **`YamlServiceLoaderExample`**: The main class that:
    - Locates the YAML configuration file.
    - Configures an `EventHandlerLoader` service with the YAML file to be loaded at startup.
    - Sets up an `InMemoryEventSource` named `yamlLoader` (matching the default group name).
    - Boots the Mongoose server with the `yamlLoaderService`.
    - Offers events to the feed, which are then processed by the handler loaded from YAML.

## Running the Example

To run the example from the project root:

```bash
mvn -pl plugins/yaml-service-loader-example exec:java -Dexec.mainClass="com.telamin.mongoose.example.yaml.loader.YamlServiceLoaderExample"
```

You should see output indicating that the server has booted and the events are being received by the custom handler defined in the YAML file:

```text
Server booted. Sending events...
CUSTOM-YAML-PREFIX:  received event: Hello from YAML-loaded handler!
CUSTOM-YAML-PREFIX:  received event: Mongoose is powerful with plugins.
```
