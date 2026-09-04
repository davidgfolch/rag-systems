package com.rag.tui.ui;

import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandPickerTest {

    private CommandRegistry registry;
    private Terminal terminal;
    private NonBlockingReader reader;
    private StringWriter terminalOutput;
    private CommandPicker sut;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        terminal = mock(Terminal.class);
        reader = mock(NonBlockingReader.class);
        terminalOutput = new StringWriter();
        when(terminal.reader()).thenReturn(reader);
        when(terminal.writer()).thenReturn(new PrintWriter(terminalOutput));
        sut = new CommandPicker(registry, terminal);
    }

    @Test
    void returnsNullOnEscape() throws IOException {
        when(reader.read(50)).thenReturn(27);

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNull();
    }

    @Test
    void returnsNullOnImmediateEscapeSequence() throws IOException {
        when(reader.read(50)).thenReturn(27, -1);

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNull();
    }

    @Test
    void selectsFirstCommandOnEnter() throws IOException {
        when(reader.read(50)).thenReturn((int) '\n');

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("help");
    }

    @Test
    void navigatesDownAndSelects() throws IOException {
        when(reader.read(50)).thenReturn(27, (int) '[', (int) 'B', (int) '\n');

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("modules");
    }

    @Test
    void navigatesDownThenUpAndSelects() throws IOException {
        when(reader.read(50)).thenReturn(27, (int) '[', (int) 'B', 27, (int) '[', (int) 'A', (int) '\n');

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("help");
    }

    @Test
    void filtersCommandsByTypedCharacters() throws IOException {
        when(reader.read(50)).thenReturn((int) 'a', (int) 's', (int) 'k', (int) '\n');

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("ask");
    }

    @Test
    void returnsNullOnReadTimeout() throws IOException {
        when(reader.read(50)).thenReturn(-1);

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNull();
    }

    @Test
    void displaysCommandListOnStart() throws IOException {
        when(reader.read(50)).thenReturn(27);

        sut.pick("");

        assertThat(terminalOutput.toString()).contains("Commands:");
        assertThat(terminalOutput.toString()).contains("help");
        assertThat(terminalOutput.toString()).contains("modules");
    }
}
