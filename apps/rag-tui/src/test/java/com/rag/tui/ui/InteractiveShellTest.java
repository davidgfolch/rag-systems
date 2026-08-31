package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractiveShellTest {

    private final CommandDispatcher dispatcher = mock(CommandDispatcher.class);

    @Test
    void loopsUntilQuit() {
        when(dispatcher.handle("ask hi"))
                .thenReturn(new CommandResult("answer: hi", false));
        when(dispatcher.handle("quit"))
                .thenReturn(new CommandResult("bye", true));

        StringWriter out = new StringWriter();
        InteractiveShell sut =
                new InteractiveShell(dispatcher, new StringReader("ask hi\nquit\n"), out);

        sut.run();

        verify(dispatcher, times(2)).handle(anyString());
        assertThat(out.toString()).contains("answer: hi");
        assertThat(out.toString()).contains("bye");
    }
}
