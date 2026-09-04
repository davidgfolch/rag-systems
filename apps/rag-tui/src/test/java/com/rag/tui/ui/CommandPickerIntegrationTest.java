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

class CommandPickerIntegrationTest {

    private Terminal terminal;
    private NonBlockingReader reader;
    private CommandPicker sut;

    @BeforeEach
    void setUp() {
        terminal = mock(Terminal.class);
        reader = mock(NonBlockingReader.class);
        when(terminal.reader()).thenReturn(reader);
        when(terminal.writer()).thenReturn(new PrintWriter(new StringWriter()));
        sut = new CommandPicker(new CommandRegistry(), terminal);
    }

    @Test
    void typingFilterThenEnterReturnsMatchingCommand() throws IOException {
        when(reader.read(50)).thenReturn((int) 'm', (int) 'o', (int) 'd', (int) '\n');

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("modules");
    }

    @Test
    void arrowDownThenEnterReturnsSecondCommand() throws IOException {
        when(reader.read(50)).thenReturn(27, (int) '[', (int) 'B', (int) '\n');

        CommandDescriptor result = sut.pick("");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("modules");
    }
}
