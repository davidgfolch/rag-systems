package com.rag.tui.ui;

import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

import static com.rag.tui.ui.Key.KeyType.BACKSPACE;
import static com.rag.tui.ui.Key.KeyType.DOWN;
import static com.rag.tui.ui.Key.KeyType.ENTER;
import static com.rag.tui.ui.Key.KeyType.ESC;
import static com.rag.tui.ui.Key.KeyType.TYPE;
import static com.rag.tui.ui.Key.KeyType.UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerminalPickSourceTest {

    private static final int EOF = -1;

    @Test
    void decodesPlainKeys() throws Exception {
        var source = new InteractivePrompter.TerminalPickSource(terminal(13, 8, 'x', 'q'));

        assertThat(source.read()).isEqualTo(new Key(ENTER, ' '));
        assertThat(source.read()).isEqualTo(new Key(BACKSPACE, ' '));
        assertThat(source.read()).isEqualTo(new Key(TYPE, 'x'));
        assertThat(source.read()).isEqualTo(new Key(ESC, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesEscapeArrowsAndVimKeys() throws Exception {
        var source = new InteractivePrompter.TerminalPickSource(
                terminal(27, '[', 'A', 27, '[', 'B', 'k', 'j', 'q', EOF));

        assertThat(source.read()).isEqualTo(new Key(UP, ' '));
        assertThat(source.read()).isEqualTo(new Key(DOWN, ' '));
        assertThat(source.read()).isEqualTo(new Key(UP, ' '));
        assertThat(source.read()).isEqualTo(new Key(DOWN, ' '));
        assertThat(source.read()).isEqualTo(new Key(ESC, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void returnsEscapeAndNullOnClosedStream() throws Exception {
        var source = new InteractivePrompter.TerminalPickSource(terminal(EOF));

        assertThat(source.read()).isNull();
    }

    private static Terminal terminal(int... bytes) throws IOException {
        Terminal terminal = mock(Terminal.class);
        when(terminal.reader()).thenReturn(new FakeReader(bytes));
        return terminal;
    }

    private static final class FakeReader extends NonBlockingReader {
        private final Queue<Integer> bytes;

        FakeReader(int[] bytes) throws IOException {
            this.bytes = new ArrayDeque<>();
            addAll(this.bytes, bytes);
        }

        @Override
        public int read(long timeout, boolean isPeek) {
            Integer next = bytes.peek();
            if (next == null) return EOF;
            if (!isPeek) {
                bytes.poll();
            }
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

        private static void addAll(Queue<Integer> q, int... ints) {
            for (int i : ints) {
                if (i == EOF) {
                    continue;
                }
                q.add(i);
            }
        }
    }
}
