package com.telamin.mongoose.example.howto;

import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.mongoose.service.feedstatus.FeedState;
import com.telamin.mongoose.service.feedstatus.FeedStatus;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Counts order lines, slowly. Its feed turns on {@code feedStatusEvents}, so it also receives the feed's
 * {@link FeedStatus} changes as ordinary events, in order with the lines: a {@code CAUGHT_UP} event arrives after the
 * last line it covers, which is where a batch processor would flush or emit a summary.
 */
public class OrderCounter extends ObjectEventHandlerNode {

    private final String feedName;
    private final long parkNanosPerLine;
    private final AtomicLong lines = new AtomicLong();

    public OrderCounter(String feedName, long parkMicrosPerLine) {
        this.feedName = feedName;
        this.parkNanosPerLine = parkMicrosPerLine * 1_000;
    }

    @Override
    public void start() {
        getContext().subscribeToNamedFeed(feedName);
    }

    @Override
    protected boolean handleEvent(Object event) {
        if (event instanceof FeedStatus status) {
            if (status.state() == FeedState.CAUGHT_UP || status.state() == FeedState.END_OF_STREAM) {
                System.out.printf("  in-graph: %s %s@%d, %d lines handled so far%n",
                        status.feedName(), status.state(), status.sequence(), lines.get());
            }
            return true;
        }
        if (event instanceof String) {
            if (parkNanosPerLine > 0) LockSupport.parkNanos(parkNanosPerLine);
            lines.incrementAndGet();
        }
        return true;
    }

    public long lines() {
        return lines.get();
    }
}
