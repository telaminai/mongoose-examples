package com.telamin.mongoose.example.howto;

import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.telamin.mongoose.service.delivery.Delivery;
import com.telamin.mongoose.service.delivery.DeliveryContext;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.LockSupport;

/**
 * A stateful, non-idempotent processor: it adds each line's amount to a running total. Adding a line twice gives the
 * wrong total, so at-least-once delivery alone is not enough for it.
 * <p>
 * After every line it saves its state, and the source position of the line it applied, in one atomic write. After a
 * restart it skips a re-delivered line whose position is at or before the saved one ({@link Delivery#alreadyApplied}).
 */
public class LedgerProcessor extends ObjectEventHandlerNode {

    private final String feedName;
    private final Path stateFile;
    private final OutputStream appliedLog;
    private final boolean deduplicate;
    private final long parkNanosPerLine;

    // the state, and the position it includes, saved together
    private long applied;
    private long total;
    private long epoch;
    private long position = -1;

    public LedgerProcessor(String feedName, Path stateFile, Path appliedLog, boolean deduplicate, long parkMicrosPerLine) {
        this.feedName = feedName;
        this.stateFile = stateFile;
        this.deduplicate = deduplicate;
        this.parkNanosPerLine = parkMicrosPerLine * 1_000;
        try {
            // restore before the feed delivers anything
            if (Files.exists(stateFile)) {
                LedgerState s = LedgerState.read(stateFile);
                applied = s.applied();
                total = s.total();
                epoch = s.epoch();
                position = s.position();
            }
            this.appliedLog = new FileOutputStream(appliedLog.toFile(), true);   // unbuffered: survives kill -9
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void start() {
        getContext().subscribeToNamedFeed(feedName);
    }

    @Override
    protected boolean handleEvent(Object event) {
        if (!(event instanceof String line)) {
            return true;
        }
        Delivery delivery = DeliveryContext.current();   // non-null for every line of a COMMITED file feed
        if (deduplicate && delivery != null && delivery.alreadyApplied(epoch, position)) {
            return false;   // re-delivered after a crash: its effect is already in the saved state
        }
        if (parkNanosPerLine > 0) {
            LockSupport.parkNanos(parkNanosPerLine);   // a slow processor, so the kill lands mid-stream
        }
        try {
            appliedLog.write((line + "\n").getBytes(StandardCharsets.UTF_8));   // the output
            applied++;
            total += Long.parseLong(line.substring(line.indexOf(',') + 1));
            if (delivery != null) {
                epoch = delivery.epoch();
                position = delivery.positionAsLong();
            }
            new LedgerState(applied, total, epoch, position).write(stateFile);   // state + position, atomically
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    /** The saved state: lines applied, their total, and the (epoch, position) of the last line applied. */
    public record LedgerState(long applied, long total, long epoch, long position) {

        static LedgerState read(Path file) throws IOException {
            String[] s = Files.readString(file).trim().split(" ");
            return new LedgerState(Long.parseLong(s[0]), Long.parseLong(s[1]), Long.parseLong(s[2]), Long.parseLong(s[3]));
        }

        /** Write to a temporary file and move it into place: a kill never leaves a torn state file. */
        void write(Path file) throws IOException {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, applied + " " + total + " " + epoch + " " + position);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
