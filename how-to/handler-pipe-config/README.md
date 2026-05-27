# Declarative HandlerPipe via `HandlerPipeConfig`

In-VM pipe between two event processors, declared as a single config
entry rather than wired programmatically.

## When to use this shape

The companion `how-to/handler-pipe` example builds the same shape
programmatically — it constructs a `HandlerPipe.of(...)`, wraps the
source side as an `EventFeedConfig`, and adds it via
`addEventFeed(...)`. That works, but it's ~20 lines of plumbing per
pipe and the sink side is implicit.

`HandlerPipeConfig` collapses that to one builder call that registers
**both halves** of the pipe under the same logical name:

```java
.addPipe(HandlerPipeConfig.builder()
        .name("orders")
        .broadcast(true)
        .agent("pipe-agent", new SleepingMillisIdleStrategy(1))
        .build())
```

After boot:
- Subscribers reach the pipe via `subscribeToNamedFeed("orders")`.
- Publishers receive the sink via `@ServiceRegistered void onSink(MessageSink, String name)`
  with `name = "orders.sink"` (default `.sink` suffix; configurable via
  `sinkName(...)` on the builder).

## Cross-thread story

`HandlerPipe` is backed by an `InMemoryEventSource` which extends
`AbstractAgentHostedEventSourceService`. Publishing into the sink
enqueues; a dedicated agent thread drains and dispatches into subscriber
read queues. Producer and consumer can sit on independent agent groups
with no explicit synchronization — verified end-to-end by the
`HandlerPipeConfigIntegrationTest` suite in `mongoose` proper.

## YAML equivalent

Identical semantics from `config/server.yml`:

```yaml
pipes:
  - name: orders
    broadcast: true
    agentName: pipe-agent
    idleStrategy: !!org.agrona.concurrent.SleepingMillisIdleStrategy {}
```

## Run

```
mvn -pl how-to/handler-pipe-config -am package
java --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
     -cp "target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout | tail -1)" \
     com.telamin.mongoose.example.howto.HandlerPipeConfigExample
```

(The `--add-exports` flag is required by Agrona's `Unsafe` access on
Java 21+. Same requirement as every other Mongoose runtime example.)

Expected output:

```
Publisher: received sink for 'orders.sink'
Subscriber: subscribed to 'orders'
Publisher: sent 3 values into the pipe
Subscriber: received 'first-order' (#1)
Subscriber: received 'second-order' (#2)
Subscriber: received 'third-order' (#3)
Subscriber received 3 event(s).
```
