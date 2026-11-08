package com.rag.tui.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.rag.tui.ui.TerminalStyle.error;
import static com.rag.tui.ui.TerminalStyle.success;

/**
 * Aggregates the terminal state of every asynchronous ingestion submitted for
 * one command and prints a single summary once the batch is closed and its last
 * job finishes. The summary is emitted from whichever poller thread completes
 * last, so the shell never blocks waiting for slow ingestions.
 */
final class IngestBatch {

    private final String label;
    private final Consumer<String> tokenSink;
    private final List<String> failures = new ArrayList<>();
    private int pending;
    private int completed;
    private int failed;
    private long chunks;
    private boolean closed;
    private boolean reported;

    IngestBatch(String label, Consumer<String> tokenSink) {
        this.label = label;
        this.tokenSink = tokenSink;
    }

    synchronized void submitted() {
        pending++;
    }

    synchronized void completed(int chunkCount) {
        pending--;
        completed++;
        chunks += chunkCount;
        emitIfDone();
    }

    synchronized void failed(String jobLabel, String message) {
        pending--;
        failed++;
        failures.add(jobLabel + ": " + message);
        emitIfDone();
    }

    synchronized void cancelled() {
        pending--;
        emitIfDone();
    }

    synchronized void close() {
        closed = true;
        emitIfDone();
    }

    private void emitIfDone() {
        if (reported || !closed || pending > 0 || completed + failed == 0) return;
        reported = true;
        tokenSink.accept(failed == 0 ? success(summary()) : error(summary()));
    }

    private String summary() {
        var text = new StringBuilder("%s: %d page(s) complete, %d failed, %d chunk(s) total.%n"
                .formatted(label, completed, failed, chunks));
        for (var failure : failures) {
            text.append(" - ").append(failure).append('\n');
        }
        return text.toString();
    }
}
