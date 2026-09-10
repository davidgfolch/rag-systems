package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end drive of {@link NoopPrompter} against a real reader: non-TTY input
 * reads plain lines and never shows a picker.
 */
class NoopPrompterIntegrationTest {

    @Test
    void readsPlainLinesAndReturnsNullOnEof() {
        var sut = new NoopPrompter(new StringReader("add-file x\nquit\n"));
        assertThat(sut.prompt("> ")).isEqualTo("add-file x");
        assertThat(sut.prompt("> ")).isEqualTo("quit");
        assertThat(sut.prompt("> ")).isNull();
    }

    @Test
    void neverPublicsPickReturnsEmpty() {
        var sut = new NoopPrompter(new StringReader(""));
        assertThat(sut.pick("title", List.of(new Prompter.Choice("a", "a")))).isEmpty();
    }
}