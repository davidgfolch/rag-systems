package com.rag.tui.ui;

import com.rag.tui.testfixture.TestTerminal;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static com.rag.tui.ui.Key.KeyType.BACKSPACE;
import static com.rag.tui.ui.Key.KeyType.DELETE;
import static com.rag.tui.ui.Key.KeyType.DOWN;
import static com.rag.tui.ui.Key.KeyType.END;
import static com.rag.tui.ui.Key.KeyType.ENTER;
import static com.rag.tui.ui.Key.KeyType.ESC;
import static com.rag.tui.ui.Key.KeyType.HOME;
import static com.rag.tui.ui.Key.KeyType.LEFT;
import static com.rag.tui.ui.Key.KeyType.NONE;
import static com.rag.tui.ui.Key.KeyType.RIGHT;
import static com.rag.tui.ui.Key.KeyType.TYPE;
import static com.rag.tui.ui.Key.KeyType.UP;
import static com.rag.tui.ui.Key.KeyType.WORD_BACKSPACE;
import static com.rag.tui.ui.Key.KeyType.WORD_LEFT;
import static com.rag.tui.ui.Key.KeyType.WORD_RIGHT;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalPickSourceTest {

    private static final int EOF = -1;

    @Test
    void decodesPlainKeys() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(13, 127, 'x', 'q'));
        assertThat(source.read()).isEqualTo(new Key(ENTER, ' '));
        assertThat(source.read()).isEqualTo(new Key(BACKSPACE, ' '));
        assertThat(source.read()).isEqualTo(new Key(TYPE, 'x'));
        assertThat(source.read()).isEqualTo(new Key(ESC, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesEscapeArrowsAndVimKeys() throws Exception {
        var source = new TerminalPickSource(
                TestTerminal.withInput(27, '[', 'A', 27, '[', 'B', 'k', 'j', 'q', EOF));
        assertThat(source.read()).isEqualTo(new Key(UP, ' '));
        assertThat(source.read()).isEqualTo(new Key(DOWN, ' '));
        assertThat(source.read()).isEqualTo(new Key(UP, ' '));
        assertThat(source.read()).isEqualTo(new Key(DOWN, ' '));
        assertThat(source.read()).isEqualTo(new Key(ESC, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void keepsVimLettersWhenNavigationIsDisabled() throws Exception {
        var source = new TerminalPickSource(
                TestTerminal.withInput('s', 'k', '-', 'o', 'r', '-', 'v', '1', EOF), false);
        assertThat(source.read()).isEqualTo(new Key(TYPE, 's'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, 'k'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '-'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, 'o'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, 'r'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '-'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, 'v'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '1'));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesSs3ApplicationArrows() throws Exception {
        var source = new TerminalPickSource(
                TestTerminal.withInput(27, 'O', 'A', 27, 'O', 'B', 27, 'O', 'C', 27, 'O', 'D'));
        assertThat(source.read()).isEqualTo(new Key(UP, ' '));
        assertThat(source.read()).isEqualTo(new Key(DOWN, ' '));
        assertThat(source.read()).isEqualTo(new Key(RIGHT, ' '));
        assertThat(source.read()).isEqualTo(new Key(LEFT, ' '));
    }

    @Test
    void returnsEscapeWhenSecondByteIsNotASequencePrefix() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(27, EOF));
        assertThat(source.read()).isEqualTo(new Key(ESC, ' '));
    }

    @Test
    void returnsEscapeAndNullOnClosedStream() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(EOF));
        assertThat(source.read()).isNull();
    }

    @Test
    void mapsCtrlCDToEscape() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(3, 4, EOF));
        assertThat(source.read()).isEqualTo(new Key(ESC, ' '));
        assertThat(source.read()).isEqualTo(new Key(ESC, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void mapsControlBytesToNoneKey() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(7, 9, 26, EOF));
        assertThat(source.read()).isEqualTo(new Key(NONE, ' '));
        assertThat(source.read()).isEqualTo(new Key(NONE, ' '));
        assertThat(source.read()).isEqualTo(new Key(NONE, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesPathPunctuationAsTypable() throws Exception {
        var source = new TerminalPickSource(
                TestTerminal.withInput(':', '\\', '/', '#', '~', '!', '[', EOF));
        assertThat(source.read()).isEqualTo(new Key(TYPE, ':'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '\\'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '/'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '#'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '~'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '!'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, '['));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesNonAsciiLettersAsTypable() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput('ñ', 'á', EOF));
        assertThat(source.read()).isEqualTo(new Key(TYPE, 'ñ'));
        assertThat(source.read()).isEqualTo(new Key(TYPE, 'á'));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesHomeAndEndKeyVariants() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(
                27, '[', 'H', 27, '[', 'F', 27, '[', '1', '~', 27, '[', '4', '~',
                27, 'O', 'H', 27, 'O', 'F', EOF));
        assertThat(source.read()).isEqualTo(new Key(HOME, ' '));
        assertThat(source.read()).isEqualTo(new Key(END, ' '));
        assertThat(source.read()).isEqualTo(new Key(HOME, ' '));
        assertThat(source.read()).isEqualTo(new Key(END, ' '));
        assertThat(source.read()).isEqualTo(new Key(HOME, ' '));
        assertThat(source.read()).isEqualTo(new Key(END, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesDeleteKey() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(27, '[', '3', '~', EOF));
        assertThat(source.read()).isEqualTo(new Key(DELETE, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesCtrlArrowAsWordMovement() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(
                27, '[', '1', ';', '5', 'D', 27, '[', '1', ';', '5', 'C', EOF));
        assertThat(source.read()).isEqualTo(new Key(WORD_LEFT, ' '));
        assertThat(source.read()).isEqualTo(new Key(WORD_RIGHT, ' '));
        assertThat(source.read()).isNull();
    }

    @Test
    void decodesCtrlBackspaceVariants() throws Exception {
        var source = new TerminalPickSource(TestTerminal.withInput(
                8, 31, 23,
                27, '[', '3', ';', '5', '~',
                27, '[', '1', '2', '7', ';', '5', 'u',
                27, '[', '8', ';', '5', 'u', EOF));
        for (int i = 0; i < 6; i++) {
            assertThat(source.read()).isEqualTo(new Key(WORD_BACKSPACE, ' '));
        }
        assertThat(source.read()).isNull();
    }

    @Test
    void promptKeepsPastedWindowsPathIntact() {
        String path = "D:\\documentos\\books\\DB\\# Confluent_Kafka_Definitive_Guide_Complete.pdf";
        Terminal terminal = TestTerminal.withInput(promptBytes("add-file " + path));
        var sut = new InteractivePrompter(terminal, (line, cursor) -> List.of());
        assertThat(sut.prompt("> ")).isEqualTo("add-file " + path);
    }

    private static int[] promptBytes(String line) {
        return IntStream.concat(line.chars(), IntStream.of(13)).toArray();
    }

    @Test
    void promptKeepsPastedApiKeyIntact() {
        Terminal terminal = TestTerminal.withInput('s', 'k', '-', 'o', 'r', '-', 'v', '1', '-', 'a', 'b', 13);
        var sut = new InteractivePrompter(terminal, (line, cursor) -> List.of());
        assertThat(sut.prompt("> ")).isEqualTo("sk-or-v1-ab");
    }
}