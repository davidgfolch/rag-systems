package com.rag.tui.testfixture;

import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.io.Writer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A mocked JLine {@link Terminal} that replays fixed bytes in place of a live tty. */
public final class TestTerminal {

    private TestTerminal() {
    }

    public static Terminal withInput(int... bytes) {
        Terminal terminal = mock(Terminal.class);
        when(terminal.reader()).thenReturn(new TestNonBlockingReader(bytes));
        when(terminal.writer()).thenReturn(new PrintWriter(Writer.nullWriter()));
        return terminal;
    }
}
