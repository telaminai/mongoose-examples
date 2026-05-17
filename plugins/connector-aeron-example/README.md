# connector-aeron-example

Round-trip example for the [`connector-aeron`](https://telaminai.github.io/mongoose-plugins/connectors/aeron/) plugin: an in-memory producer feed, an `AeronMessageSink` that writes to an `aeron:ipc` channel, and an `AeronArchiveEventSource` (LIVE mode) that reads back from the same channel. No external infrastructure — the embedded Aeron `MediaDriver` is launched in-process.

## Wire diagram

```
  InMemoryEventSource "producer"
          │ String events
          ▼
  ProducerHandler ──► AeronMessageSink ───┐
                                           │ aeron:ipc, stream 10
                                           ▼
                           AeronArchiveEventSource (LIVE)
                                           │
                                           ▼
                                   ConsumerHandler  ──► System.out
```

## Files

- [`AeronRoundTripExample`](src/main/java/com/telamin/mongoose/example/aeron/AeronRoundTripExample.java) — boots the server with both halves wired and offers three sample strings.
- [`AeronRoundTripExampleTest`](src/test/java/com/telamin/mongoose/example/aeron/AeronRoundTripExampleTest.java) — asserts the events round-trip through the IPC channel using [`MongooseTestHarness`](https://telaminai.github.io/mongoose-plugins/test-support/).

## Run it

```bash
mvn -pl plugins/connector-aeron-example test    # asserted round-trip via the harness
mvn -pl plugins/connector-aeron-example exec:java -Dexec.mainClass=com.telamin.mongoose.example.aeron.AeronRoundTripExample
```

Expected console output:
```
PRODUCED: hello
CONSUMED: hello
PRODUCED: aeron
CONSUMED: aeron
PRODUCED: world
CONSUMED: world
```

## JVM flags

Aeron 1.48 needs `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED` on JDK 21+. Surefire is wired for it by inheritance from the mongoose-examples parent; for `exec:java` runs add it to `MAVEN_OPTS` or the launcher.

## What this proves

- The Aeron connector plugin works through a real `MongooseServer`, not just in a unit test.
- The embedded `MediaDriver` flag (`launchEmbeddedDriver=true`) is sufficient for dev / test / demo setups — no separate driver process needed.
- LIVE-mode subscription delivers events in order.
- The `MongooseTestHarness` integrates plugin connectors without per-test boot ceremony.

## Production differences

For real deployments:

- **Bring your own MediaDriver.** Set `launchEmbeddedDriver=false` and configure `aeronDirectoryName` to point at the shared driver's CnC directory. Embedded driver is fine for tests but conflicts with cluster usage.
- **Use UDP, not IPC.** `aeron:udp?endpoint=224.0.1.1:40456` for LAN multicast or `aeron:udp?endpoint=host:port` for unicast.
- **Persist with an archive.** Pair with Aeron Archive and `Mode.ARCHIVE` for cold-start replay.

See the [connector-aeron catalogue page](https://telaminai.github.io/mongoose-plugins/connectors/aeron/) for the full config reference.
