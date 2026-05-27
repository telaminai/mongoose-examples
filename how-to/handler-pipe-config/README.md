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
Java 21+.)

The example boots the server, wires the admin web on **http://127.0.0.1:8186**,
fires three initial values into the pipe, then keeps publishing one
"tick-N" value per second for five minutes so you can browse the admin
and see ongoing activity.

## What to look at in the admin

Open **http://127.0.0.1:8186** and:

- **Overview → Pipes card** — one row showing the configured pipe:
  feed name (`orders`), sink name (`orders.sink`), agent
  (`pipe-agent`), and flags (`bcast` for broadcast). This is the
  card that exists because of the new `/api/pipes` endpoint —
  pipes are tracked as one logical entity instead of two
  unrelated services.
- **Overview → Feeds + Sinks cards** — *do not* show `orders` or
  `orders.sink`. The pipe halves are deliberately filtered out so
  they aren't double-counted; the Pipes card has them.
- **Topology view** — single diamond-shaped pipe node (teal) instead
  of two separate nodes. **Both directions wired** on the same node:
    - incoming arrows from the `publisher-agent` group
    - outgoing arrows to the `subscriber-agent` group
  This is the visual that distinguishes a pipe from a one-direction
  feed (circle, output only) or sink (round-rectangle, input only).
- **Services list view** — you can still see the raw `orders` (feed)
  and `orders.sink` (sink) entries if you want to inspect them as
  individual services. That's the underlying truth; the Pipes card
  is the higher-level grouping.
- **`/api/pipes`** — raw JSON:
  ```json
  { "pipes": [
      { "name": "orders", "sinkName": "orders.sink",
        "agentName": "pipe-agent", "broadcast": true,
        "cacheEventLog": false }
  ] }
  ```

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
