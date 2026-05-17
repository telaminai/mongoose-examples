# yaml-service-loader-example

Boots a Mongoose server that loads an event-processor topology from a YAML
config file using the [`svc-loader-yaml`](https://telaminai.github.io/mongoose-plugins/services/loader-yaml/)
plugin, then feeds it events from an in-memory source.

## What this shows

- `EventHandlerLoader` (from `svc-loader-yaml`) is registered as a service.
- Its `loadAtStartup` set points at `log-processor.yaml`, which declares one node
  — `YamlLogHandler` — under `namedNodes`.
- When the server boots, the loader runs SnakeYAML to instantiate the node,
  hands it to `Fluxtion.compile(...)`, and registers the compiled processor
  with the server.
- An `InMemoryEventSource` named `yamlLoader` then publishes a few events, which
  the YAML-loaded handler logs.

## Files

| Path | What it is |
|---|---|
| `YamlServiceLoaderExample.java` | The `main` that wires the feed, the loader service, and boots the server. |
| `YamlLogHandler.java` | An `ObjectEventHandlerNode` that prints incoming events. Instantiated by SnakeYAML from the YAML's `!!` tag. |
| `src/main/resources/yaml/log-processor.yaml` | Processor topology consumed by `svc-loader-yaml`. |

## Running

From the repo root:

```bash
mvn -pl plugins/yaml-service-loader-example -am package
mvn -pl plugins/yaml-service-loader-example exec:exec
```

Expected output (interleaved with Mongoose lifecycle logs):

```
Sending events...
YAML-LOADED-HANDLER: Event #0
YAML-LOADED-HANDLER: Event #1
YAML-LOADED-HANDLER: Event #2
YAML-LOADED-HANDLER: Event #3
YAML-LOADED-HANDLER: Event #4
```

The `YAML-LOADED-HANDLER:` prefix comes from the `prefix` field set in the YAML
— change it there and rerun to see the value flow in via SnakeYAML bean-setter.

## Notes for adapters

- **`exec:exec`, not `exec:java`.** The Agrona runtime needs three
  `--add-opens=java.base/...` flags. These are JVM args, so they only take effect
  in a forked JVM (`exec:exec`). Under `exec:java` they'd be silently passed to
  `main(String[])` instead.
- **SnakeYAML 1.33 is pinned.** SnakeYAML 2.x blocks the
  `!!fully.qualified.ClassName` global-tag syntax by default. The `svc-loader-yaml`
  plugin uses `new Yaml()` (no `TagInspector`), so the safest dep choice for
  the YAML below is 1.33. The pin can be dropped once `svc-loader-yaml` ships a
  release that allows global tags under SnakeYAML 2.x.
- **`mongoose` 1.0.9+.** Earlier versions don't start event-processor agents
  registered by services *during* `start()` — they get registered in the live
  map but missed by the snapshot iteration in `MongooseServer.start()`. The
  processor would init but never receive events. Fixed in 1.0.9-SNAPSHOT by a
  late-start pass after `LifecycleManager.start()` returns.
