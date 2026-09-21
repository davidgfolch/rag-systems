package com.rag.tui.ui;

import com.rag.tui.testfixture.TestTerminal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link InteractivePrompter#prompt(String)} end-to-end through a real
 * {@link TerminalPickSource} decoding raw escape-sequence bytes, proving that
 * Home/End/Delete, Ctrl+Left/Right and Ctrl+Backspace perform the usual text
 * and cursor control. The tty is a scripted {@link TestTerminal} mock, so the
 * test is deterministic and platform-independent.
 */
class TerminalPickSourceIntegrationTest {

    private static final String ENTER = "\r";
    private static final String HOME = "\u001B[H";
    private static final String END = "\u001B[F";
    private static final String DELETE = "\u001B[3~";
    private static final String WORD_LEFT = "\u001B[1;5D";
    private static final String WORD_RIGHT = "\u001B[1;5C";
    private static final String CTRL_BACKSPACE = "\u001B[3;5~";
    private static final String BACKSPACE = "\u0008";

    @Test
    void homeDeleteAndEndEditTheLine() {
        assertThat(prompt("abc" + HOME + DELETE + END + "d" + ENTER)).isEqualTo("bcd");
    }

    @Test
    void deleteKeyRemovesCharacterUnderCursor() {
        assertThat(prompt("abc" + HOME + DELETE + ENTER)).isEqualTo("bc");
    }

    @Test
    void ctrlLeftJumpsToPreviousWordStart() {
        assertThat(prompt("foo bar" + WORD_LEFT + "X" + ENTER)).isEqualTo("foo Xbar");
    }

    @Test
    void ctrlRightJumpsToNextWordStart() {
        assertThat(prompt("foo bar" + HOME + WORD_RIGHT + "X" + ENTER)).isEqualTo("foo Xbar");
    }

    @Test
    void ctrlBackspaceDeletesPreviousWord() {
        assertThat(prompt("foo bar" + CTRL_BACKSPACE + ENTER)).isEqualTo("foo ");
    }

    @Test
    void simpleBackspaceDeletesOneCharacter() {
        assertThat(prompt("abcd" + BACKSPACE + ENTER)).isEqualTo("abc");
    }

    private static String prompt(String line) {
        var terminal = TestTerminal.withInput(line.chars().toArray());
        var sut = new InteractivePrompter(terminal, (value, cursor) -> List.of());
        return sut.prompt("> ");
    }
}
