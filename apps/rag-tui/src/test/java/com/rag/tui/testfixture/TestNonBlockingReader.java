package com.rag.tui.testfixture;

import org.jline.utils.NonBlockingReader;

import java.util.ArrayDeque;
import java.util.Queue;

/** A {@link NonBlockingReader} that replays a fixed byte script, then reports EOF. */
public final class TestNonBlockingReader extends NonBlockingReader {

    private static final int EOF = -1;

    private final Queue<Integer> bytes = new ArrayDeque<>();

    public TestNonBlockingReader(int... input) {
        for (int code : input) {
            if (code != EOF) bytes.add(code);
        }
    }

    @Override
    public int read(long timeout, boolean isPeek) {
        Integer next = bytes.peek();
        if (next == null) return EOF;
        if (!isPeek) bytes.poll();
        return next;
    }

    @Override
    public int read() {
        Integer next = bytes.poll();
        return next == null ? EOF : next;
    }

    @Override
    public int readBuffered(char[] b, int off, int len, long timeout) {
        int i = off;
        while (i < off + len && !bytes.isEmpty()) {
            b[i++] = (char) bytes.poll().intValue();
        }
        return i == off ? EOF : i - off;
    }

    @Override
    public int available() {
        return bytes.size();
    }

    @Override
    public void close() {
        bytes.clear();
    }
}
