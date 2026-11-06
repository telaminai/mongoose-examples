package com.telamin.mongoose.example.howto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Process a file feed at least once, across a {@code kill -9}.
 * <p>
 * For each scenario (without, then with, consumer-side de-duplication) it:
 * <ol>
 *     <li>writes a 5,000-line ledger file;</li>
 *     <li>starts {@link AtLeastOnceServer} in a child JVM, a {@code COMMITED} file feed into a slow, stateful
 *     processor;</li>
 *     <li>kills the child with SIGKILL part way through, and compares the read pointer with the saved state;</li>
 *     <li>restarts it, lets it run until {@code feedStatus} says the feed has settled, and checks the output.</li>
 * </ol>
 * No line is ever lost. Without de-duplication the lines processed after the last commit are applied a second time,
 * so the total is wrong; with it, every line is applied exactly once.
 */
public class AtLeastOnceExample {

    static final int LINES = 5_000;
    static final int KILL_AFTER = 2_000;

    public static void main(String[] args) throws Exception {
        Path root = Path.of("target", "at-least-once-run").toAbsolutePath();
        boolean ok = scenario(root.resolve("no-dedupe"), false);
        ok &= scenario(root.resolve("dedupe"), true);
        System.out.println();
        System.out.println(ok ? "RESULT: no line lost in either run" : "RESULT: FAILED");
        System.exit(ok ? 0 : 1);
    }

    static boolean scenario(Path dir, boolean deduplicate) throws Exception {
        System.out.println();
        System.out.println("=== " + (deduplicate ? "with" : "without") + " DeliveryContext de-duplication ===");
        deleteRecursively(dir);
        Files.createDirectories(dir);
        long expectedTotal = writeLedger(dir.resolve("ledger.csv"));

        // run 1: kill -9 part way through
        Process first = startServer(dir, deduplicate, 300, "run1.log");
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (applied(dir) < KILL_AFTER && first.isAlive() && System.nanoTime() < end) {
            Thread.sleep(2);
        }
        if (!first.isAlive()) {
            System.out.println("the server exited early; see " + dir.resolve("run1.log"));
            return false;
        }
        first.destroyForcibly();   // SIGKILL on Linux and macOS: no shutdown hook, no clean stop
        first.waitFor();
        LedgerProcessor.LedgerState atKill = LedgerProcessor.LedgerState.read(dir.resolve("state.txt"));
        long pointer = readPointerOffset(dir.resolve("ledger.csv.readPointer"));
        System.out.printf("killed: %d lines applied, saved position %d, read pointer %d (never ahead of the state: %s)%n",
                atKill.applied(), atKill.position(), pointer, pointer <= atKill.position());

        // run 2: restart; it resumes from the read pointer and stops once the feed has settled
        Process second = startServer(dir, deduplicate, 0, "run2.log");
        if (!second.waitFor(60, TimeUnit.SECONDS)) {
            second.destroyForcibly();
            System.out.println("the restarted server did not settle; see " + dir.resolve("run2.log"));
            return false;
        }
        for (String line : Files.readAllLines(dir.resolve("run2.log"))) {
            if (line.startsWith("SETTLED ")) System.out.println("restart settled: " + line.substring(8));
        }

        List<String> output = Files.readAllLines(dir.resolve("applied.log"));
        Set<String> distinct = new HashSet<>(output);
        int missing = 0;
        for (int i = 1; i <= LINES; i++) {
            if (!distinct.contains(ledgerLine(i))) missing++;
        }
        LedgerProcessor.LedgerState state = LedgerProcessor.LedgerState.read(dir.resolve("state.txt"));
        System.out.printf("lines missing: %d, output lines written twice: %d%n", missing, output.size() - distinct.size());
        System.out.printf("state: applied %d (expected %d), total %d (expected %d)%s%n", state.applied(), LINES,
                state.total(), expectedTotal, state.total() == expectedTotal ? "" : "  <- lines applied twice");
        return missing == 0 && pointer <= atKill.position() && (!deduplicate || state.total() == expectedTotal);
    }

    static Process startServer(Path dir, boolean deduplicate, long parkMicros, String log) throws IOException {
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> command = new ArrayList<>(List.of(java,
                "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",   // Agrona's UnsafeApi
                "-cp", System.getProperty("java.class.path"),
                AtLeastOnceServer.class.getName(), dir.toString(), String.valueOf(deduplicate), String.valueOf(parkMicros)));
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(dir.resolve(log).toFile()).start();
    }

    static String ledgerLine(int i) {
        return "txn-" + i + "," + (i % 100 + 1);
    }

    static long writeLedger(Path file) throws IOException {
        StringBuilder b = new StringBuilder();
        long total = 0;
        for (int i = 1; i <= LINES; i++) {
            b.append(ledgerLine(i)).append('\n');
            total += i % 100 + 1;
        }
        Files.writeString(file, b, StandardCharsets.UTF_8);
        return total;
    }

    static long applied(Path dir) {
        try {
            return LedgerProcessor.LedgerState.read(dir.resolve("state.txt")).applied();
        } catch (Exception e) {
            return 0;   // not written yet, or between the write and the move
        }
    }

    /** The committed byte offset: the first 8 bytes of the pointer file. Read here only to show it. */
    static long readPointerOffset(Path pointerFile) throws IOException {
        return Files.exists(pointerFile) ? ByteBuffer.wrap(Files.readAllBytes(pointerFile), 0, 8).getLong() : 0;
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            for (Path p : paths.sorted((a, b) -> b.compareTo(a)).toList()) Files.delete(p);
        }
    }
}
