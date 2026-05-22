# Performance monitoring example

Self-contained example showing how to enable Mongoose's counters service and read the values it produces. Pairs with the
[how-to-performance-monitoring](../../../mongoose/docs/example/how-to/how-to-performance-monitoring.md) guide.

## Run

```bash
mvn -pl how-to/performance-monitoring -am package
mvn -pl how-to/performance-monitoring exec:java \
    -Dexec.mainClass=com.telamin.mongoose.example.howto.PerformanceMonitoringExample
```

Output looks like this (label values will differ — the per-subscriber queue path includes an identity hash that changes
each run):

```
=== Mongoose performance-monitoring example ===

Handler saw 1000 events
Counters (operational=true):
----------------------------------------------------------
  feed.ticker-feed.published                              1000
  group.ticker-agent.idleCycles                           87
  group.ticker-agent.processed                            42
  queue./feed/ticker-feed/subscriber/...#abcd1234.depth   0
----------------------------------------------------------
```

## What it shows

- **YAML-free programmatic enable** — `PerformanceMonitoringConfig.setEnabled(true)` plus `cfg.setPerformanceMonitoring(perf)`
  on `MongooseServerConfig`. The deployment-time YAML equivalent is two lines (see the how-to doc).
- **Counter label conventions** — flat dotted labels: `feed.{name}.published`, `group.{name}.processed` /
  `.idleCycles`, `queue.{path}.depth`. Sortable, parse-by-prefix.
- **Reading via `forEachCounter`** — allocation-free walk, suitable for a 1 Hz sampler tick. The example does it once
  at the end of the run; a real exporter loops.
- **`isOperational()` accessor** — distinguishes the real (Agrona-backed) impl from the no-op. Plugins that depend
  on counter values should consult this before treating a zero read as meaningful.

## Adding per-processor + per-node counters

Bind a `PerformanceMonitorAudit` to your processor:

```java
import com.telamin.fluxtion.Fluxtion;
import com.telamin.mongoose.service.counters.PerformanceMonitorAudit;

DataFlow flow = Fluxtion.compile(cfg -> {
    cfg.addNode(handler, "tickerProcessor");
    cfg.addAuditor(new PerformanceMonitorAudit("ticker"), "perfMon");
});
```

That adds two more counter groups:

- `processor.ticker.events` — one per event dispatched into the SEP
- `node.ticker.{nodeName}.invocations` — one per node fire

No binding → no extra bytecode in the generated SEP → zero overhead, ever.

## See also

- [How to enable performance monitoring](../../../mongoose/docs/example/how-to/how-to-performance-monitoring.md)
- [`MongooseCountersService` API](https://javadoc.io/doc/com.telamin/mongoose/latest/com/telamin/mongoose/service/counters/MongooseCountersService.html)
- [svc-admin-web — the live browser console that renders these counters](https://github.com/telaminai/mongoose-plugins/tree/main/service/svc-admin-web)
