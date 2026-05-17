# spring-service-loader-example

Boots a Mongoose server that loads an event-processor topology from a Spring
XML bean graph using the [`svc-loader-spring`](https://telaminai.github.io/mongoose-plugins/services/loader-spring/)
plugin, then feeds it events from an in-memory source.

This is the brownfield-enterprise wedge: existing Spring-wired object graphs
become Fluxtion processors without rewriting the topology.

## What this shows

- `SpringEventHandlerLoader` (from `svc-loader-spring`) is registered as a service.
- Its `loadAtStartup` set points at `log-processor.xml`, which declares one bean
  — `logHandler` of type `SpringLogHandler`.
- When the server boots, the loader runs `FluxtionSpring.compile(...)` to walk
  the bean graph, generate the Fluxtion processor source, compile it, and
  register the result with the server.
- An `InMemoryEventSource` named `springLoader` then publishes a few events,
  which the Spring-loaded handler logs.

## Files

| Path | What it is |
|---|---|
| `SpringServiceLoaderExample.java` | The `main` that wires the feed, the loader service, and boots the server. |
| `SpringLogHandler.java` | An `ObjectEventHandlerNode` that prints incoming events. Instantiated by Spring from the `<bean>` definition. |
| `src/main/resources/spring/log-processor.xml` | Bean graph consumed by `svc-loader-spring`. |

## Running

From the repo root:

```bash
mvn -pl plugins/spring-service-loader-example -am package
mvn -pl plugins/spring-service-loader-example exec:exec
```

Expected output (interleaved with Mongoose lifecycle logs):

```
Sending events...
SPRING-LOADED-HANDLER: Event #0
SPRING-LOADED-HANDLER: Event #1
SPRING-LOADED-HANDLER: Event #2
SPRING-LOADED-HANDLER: Event #3
SPRING-LOADED-HANDLER: Event #4
```

The `SPRING-LOADED-HANDLER:` prefix comes from the `<property name="prefix" value="..."/>` set in the XML — change it there and rerun to see the value flow in via Spring bean-property setter.

## Notes for adapters

- **`exec:exec`, not `exec:java`.** The Agrona runtime needs three
  `--add-opens=java.base/...` flags. These are JVM args, so they only take effect
  in a forked JVM (`exec:exec`). Under `exec:java` they'd be silently passed to
  `main(String[])` instead.
- **Spring context dep is `compile`-scope here.** `svc-loader-spring` declares
  `spring-context` as `provided` so it doesn't force a Spring version on
  non-Spring consumers. This example brings its own `spring-context:6.2.1` —
  Spring Framework 6 / Jakarta EE namespace.
- **`mongoose` >= 1.0.9 required.** Earlier versions don't start event-processor
  agents registered by services *during* `start()` — the processor would init
  but never receive events. The fix is a late-start pass in `MongooseServer.start()`.
- **Companion to [`yaml-service-loader-example`](../yaml-service-loader-example/)** —
  same shape, different authoring surface. YAML for ops/config use cases;
  Spring XML for brownfield integration with existing bean graphs.
