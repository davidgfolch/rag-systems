package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

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
                .thenReturn("answer: hi");
        when(dispatcher.handle(eq("quit"), any()))
                .thenThrow(new ShellExitException());

        var out = new StringWriter();
        var sut = new InteractiveShell(dispatcher, new NoopPrompter(new StringReader("ask hi\nquit\n")), out);

        sut.run();

        verify(dispatcher, times(2)).handle(anyString(), any());
        assertThat(out.toString()).contains("answer: hi");
        assertThat(out.toString()).contains("Bye.");
    }

    @Test
    void colorsPlainResponsesButKeepsStyledOutputUnchanged() {
        when(dispatcher.handle(eq("documents"), any())).thenReturn("Documents:\n - d1");
        when(dispatcher.handle(eq("quit"), any())).thenThrow(new ShellExitException());

        var out = new StringWriter();
        var sut = new InteractiveShell(dispatcher, new NoopPrompter(new StringReader("documents\nquit\n")), out);

        sut.run();

        var text = out.toString();
        assertThat(text)
                .contains("\033[36mDocuments:")
                .contains("Bye.");
    }

    @Test
    void streamsTokensViaSink() {
        when(dispatcher.handle(eq("ask a"), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> token = invocation.getArgument(1);
                    token.accept("Hel");
                    token.accept("lo");
                    return "done";
                });

        var out = new StringWriter();
        var sut = new InteractiveShell(dispatcher, new NoopPrompter(new StringReader("ask a\n")), out);

        sut.run();

        assertThat(out.toString()).contains("Hel").contains("lo");
    }

    @Test
    void printsProviderSummaryAtStartupWhenAvailable() {
        when(dispatcher.providerSummary()).thenReturn("Chat model: ollama/phi4\nEmbedding model: ollama/nomic-embed-text");
        when(dispatcher.handle(eq("quit"), any())).thenThrow(new ShellExitException());

        var out = new StringWriter();
        var sut = new InteractiveShell(dispatcher, new NoopPrompter(new StringReader("quit\n")), out);

        sut.run();

        assertThat(out.toString())
                .contains("RAG TUI")
                .contains("Chat model: ollama/phi4")
                .contains("Embedding model: ollama/nomic-embed-text")
                .contains("Bye.");
    }

    @Test
    void skipsProviderSummaryWhenUnreachable() {
        when(dispatcher.providerSummary()).thenReturn("");
        when(dispatcher.handle(eq("quit"), any())).thenThrow(new ShellExitException());

        var out = new StringWriter();
        var sut = new InteractiveShell(dispatcher, new NoopPrompter(new StringReader("quit\n")), out);

        sut.run();

        String text = out.toString();
        assertThat(text).doesNotContain("Providers:");
        assertThat(text).contains("Bye.");
    }

    @Test
    void wrapsTerminalWriteFailure() {
        Writer failing = new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("terminal gone");
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };

        var sut = new InteractiveShell(dispatcher, new NoopPrompter(new StringReader("ask hi\n")), failing);

        assertThatThrownBy(sut::run)
                .isInstanceOf(InteractiveShell.ShellException.class)
                .hasMessageContaining("Terminal I/O error");
    }

    @Test
    void survivesCommandFailuresAndKeepsLooping() {
        when(dispatcher.handle(eq("add-file x"), any()))
                .thenThrow(new RuntimeException("Module unreachable: Connection refused"));
        when(dispatcher.handle(eq("quit"), any()))
                .thenThrow(new ShellExitException());

        var out = new StringWriter();
        var sut = new InteractiveShell(dispatcher, new NoopPrompter(new StringReader("add-file x\nquit\n")), out);

        sut.run();

        assertThat(out.toString()).contains("Error: Module unreachable");
        assertThat(out.toString()).contains("Bye.");
    }
}
