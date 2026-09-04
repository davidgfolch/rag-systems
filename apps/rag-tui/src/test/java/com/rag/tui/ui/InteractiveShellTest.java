package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

class InteractiveShellTest {

    private final CommandDispatcher dispatcher = mock(CommandDispatcher.class);

    @Test
    void loopsUntilQuit() {
        when(dispatcher.handle(eq("ask hi"), any()))
                .thenReturn(new CommandResult("answer: hi", false));
        when(dispatcher.handle(eq("quit"), any()))
                .thenReturn(new CommandResult("bye", true));

        StringWriter out = new StringWriter();
        InteractiveShell sut =
                new InteractiveShell(dispatcher, new StringReader("ask hi\nquit\n"), out, null);

        sut.run();

        verify(dispatcher, times(2)).handle(anyString(), any());
        assertThat(out.toString()).contains("answer: hi");
        assertThat(out.toString()).contains("bye");
    }

    @Test
    void streamsTokensViaSink() {
        when(dispatcher.handle(eq("ask a"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> token = invocation.getArgument(1);
                    token.accept("Hel");
                    token.accept("lo");
                    return new CommandResult("done", true);
                });

        StringWriter out = new StringWriter();
        InteractiveShell sut =
                new InteractiveShell(dispatcher, new StringReader("ask a\n"), out, null);

        sut.run();

        assertThat(out.toString()).contains("Hel").contains("lo");
    }

    @Test
    void wrapsTerminalIoFailure() {
        Reader failing = new Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("terminal gone");
            }

            @Override
            public void close() {
                // no-op: this reader's close is never invoked by the shell
            }
        };

        InteractiveShell sut = new InteractiveShell(dispatcher, failing, new StringWriter(), null);

        assertThatThrownBy(sut::run)
                .isInstanceOf(InteractiveShell.ShellException.class)
                .hasMessageContaining("Terminal I/O error");
    }

    @Test
    void survivesCommandFailuresAndKeepsLooping() {
        when(dispatcher.handle(eq("add-file x"), any()))
                .thenThrow(new RuntimeException("Module unreachable: Connection refused"));
        when(dispatcher.handle(eq("quit"), any()))
                .thenReturn(new CommandResult("bye", true));

        StringWriter out = new StringWriter();
        InteractiveShell sut =
                new InteractiveShell(dispatcher, new StringReader("add-file x\nquit\n"), out, null);

        sut.run();

        assertThat(out.toString()).contains("Error: Module unreachable");
        assertThat(out.toString()).contains("bye");
    }
}