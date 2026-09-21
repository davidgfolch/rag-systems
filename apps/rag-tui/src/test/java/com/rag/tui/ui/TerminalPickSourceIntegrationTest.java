package com.rag.tui.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link InteractivePrompter#prompt(String)} through a real JLine
 * terminal fed with raw escape-sequence bytes, proving that Home/End/Delete,
 * Ctrl+Left/Right and Ctrl+Backspace perform the usual text and cursor control.
 */
class TerminalPickSourceIntegrationTest {

    private static final String ENTER = "\r";
    private static final String HOME = "\u001B[H";
    private static final String END = "\u001B[F";
    private static final String DELETE = "\u001B[3~";
    private static final String WORD_LEFT = "\u001B[1;5D";
    private static final String WORD_RIGHT = "\u001B[1;5C";
    private static final String CTRL_BACKSPACE = "\u0008";

    @Test
    void homeDeleteAndEndEditTheLine() throws IOException {
        assertThat(prompt("abc" + HOME + DELETE + END + "d" + ENTER)).isEqualTo("bcd");
    }

    @Test
    void deleteKeyRemovesCharacterUnderCursor() throws IOException {
        assertThat(prompt("abc" + HOME + DELETE + ENTER)).isEqualTo("bc");
    }

    @Test
    void ctrlLeftJumpsToPreviousWordStart() throws IOException {
        assertThat(prompt("foo bar" + WORD_LEFT + "X" + ENTER)).isEqualTo("foo Xbar");
    }

    @Test
    void ctrlRightJumpsToNextWordStart() throws IOException {
        assertThat(prompt("foo bar" + HOME + WORD_RIGHT + "X" + ENTER)).isEqualTo("foo Xbar");
    }

    @Test
    void ctrlBackspaceDeletesPreviousWord() throws IOException {
        assertThat(prompt("foo bar" + CTRL_BACKSPACE + ENTER)).isEqualTo("foo ");
    }

    private static String prompt(String bytes) throws IOException {
        var input = new ByteArrayInputStream(bytes.getBytes(StandardCharsets.UTF_8));
        var output = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder().system(false).dumb(true)
                .streams(input, output).build();
        try {
            var sut = new InteractivePrompter(terminal, (line, cursor) -> List.of());
            return sut.prompt("> ");
        } finally {
            terminal.close();
        }
    }
}
