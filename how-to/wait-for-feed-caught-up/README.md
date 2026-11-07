# How To: Wait Until a Feed Has Caught Up

**Mongoose project homepage:** https://telaminai.github.io/mongoose/

> **Needs Mongoose 1.1.3 or later.** This module pins `mongoose.version` to `1.1.3`.

Instead of sleeping until a run goes quiet, ask the server whether every feed has delivered what is available to it
and the processors have handled it. This example:

- reads a 20,000-line file with a `FileEventSource` into a deliberately slow processor;
- polls `MongooseServer.feedStatus("orders")` until the report is `settled`, then checks `lossless`;
- prints the same report with the `feed.status --json orders` admin command;
- appends 5,000 more lines and waits for the feed to settle again;
- turns on `feedStatusEvents` for the feed, so [OrderCounter](src/main/java/com/telamin/mongoose/example/howto/OrderCounter.java)
  also receives `FeedStatus` events in the graph, in order with the lines.

The main class is [WaitForCaughtUpExample](src/main/java/com/telamin/mongoose/example/howto/WaitForCaughtUpExample.java).

## Running the example

```bash
# from the repository root
./mvnw -pl how-to/wait-for-feed-caught-up -am compile
cd how-to/wait-for-feed-caught-up
../../mvnw compile exec:exec

# keep the server running with an interactive admin prompt; type: feed.status --json orders
../../mvnw compile exec:exec -Dexample.args=cli
```

Example output (abridged):

```text
  waiting: BEHIND seq=0 dispatched=9135/1 paused=true stalled=null
  in-graph: orders CAUGHT_UP@20000, 20000 lines handled so far
settled after 1356 ms with 20000 lines handled
feed.status --json orders:
  {"settled":true,"lossless":true,"fallbackFeeds":[],"feeds":[{"feed":"orders","state":"CAUGHT_UP",...}]}
  in-graph: orders CAUGHT_UP@25000, 25000 lines handled so far
settled again with 25000 lines handled
feed.status orders:
feeds: settled=true lossless=true
	orders: CAUGHT_UP unbounded seq=25000 drained=true policy=PAUSE dropped=0 refused=0 acknowledged=0
		orders-agent/orders/onEventCallBack: 25004/25004
```

`seq` is the position at which the feed last reported its state, not a live counter, and `paused=true` shows the file
feed's default `PAUSE` policy at work: the source waits for the slow processor instead of dropping lines.

## Further reading

- [Wait until a feed has caught up](https://telaminai.github.io/mongoose/example/how-to/how-to-wait-for-feed-caught-up/)
