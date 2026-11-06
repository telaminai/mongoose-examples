# How To: Process a File Feed at Least Once

**Mongoose project homepage:** https://telaminai.github.io/mongoose/

> **Needs Mongoose 1.1.3, which is not released yet.** This module pins `mongoose.version` to `1.1.3-SNAPSHOT` and is
> built only with the root profile `-Pmongoose-1.1.3`. Install a 1.1.3-SNAPSHOT build of Mongoose into your local
> Maven repository first. Change the pin to `1.1.3` once it is published.

A `FileEventSource` with the `COMMITED` read strategy commits its read pointer only after every subscriber has
processed a line, and (by default, for file feeds) pauses instead of dropping when a queue is full. After a crash, a
restart re-delivers what was not yet committed: duplicates, never a gap.

This example proves it with a real `kill -9`. For each of two scenarios it:

1. writes a 5,000-line ledger file (`txn-N,amount`);
2. starts [AtLeastOnceServer](src/main/java/com/telamin/mongoose/example/howto/AtLeastOnceServer.java) in a child JVM:
   a `COMMITED` file feed into [LedgerProcessor](src/main/java/com/telamin/mongoose/example/howto/LedgerProcessor.java),
   a slow, stateful processor that keeps a running total and saves it, with the line's source position, atomically
   after every line;
3. kills the child with SIGKILL after about 2,000 lines, and shows that the read pointer is not ahead of the saved
   state;
4. restarts it, waits until `feedStatus` says the feed has settled, and checks the output.

The first scenario does not de-duplicate: no line is lost, but the lines processed after the last commit are applied
twice and the total is wrong. The second skips a re-delivered line with `DeliveryContext` and
`Delivery.alreadyApplied`: every line is applied exactly once.

The driver is [AtLeastOnceExample](src/main/java/com/telamin/mongoose/example/howto/AtLeastOnceExample.java).

## Running the example

```bash
# from the repository root, once 1.1.3-SNAPSHOT is in your local repository
./mvnw -Pmongoose-1.1.3 -pl how-to/file-feed-at-least-once -am compile
cd how-to/file-feed-at-least-once
../../mvnw compile exec:exec
```

`exec:exec` (not `exec:java`) runs the example in its own JVM with this module's classpath, which the driver passes to
the child server. Files, including each server's log (`run1.log`, `run2.log`), are written under
`target/at-least-once-run`.

Example output (the exact counts vary from run to run):

```text
=== without DeliveryContext de-duplication ===
killed: 2002 lines applied, saved position 22755, read pointer 22733 (never ahead of the state: true)
restart settled: {"settled":true,"lossless":true,...,"policy":"PAUSE","dropped":0,...}
lines missing: 0, output lines written twice: 2
state: applied 5002 (expected 5000), total 252505 (expected 252500)  <- lines applied twice

=== with DeliveryContext de-duplication ===
killed: 2003 lines applied, saved position 22766, read pointer 22755 (never ahead of the state: true)
restart settled: {"settled":true,"lossless":true,...,"policy":"PAUSE","dropped":0,...}
lines missing: 0, output lines written twice: 0
state: applied 5000 (expected 5000), total 252500 (expected 252500)

RESULT: no line lost in either run
```

With de-duplication, one output line can still be written twice: the line whose output was written just before the
kill, and whose state was not yet saved. Its effect on the state is applied once.

## Further reading

- [Process a file feed at least once](https://telaminai.github.io/mongoose/example/how-to/how-to-file-feed-at-least-once/)
- [De-duplicate re-delivered events with DeliveryContext](https://telaminai.github.io/mongoose/example/how-to/how-to-deduplicate-with-delivery-context/)
