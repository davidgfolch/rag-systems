package com.rag.tui.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end drive of {@link InteractivePrompter} through a real JLine terminal
 * backed by piped byte streams: typing filters the list, Enter returns the
 * selected value, and Esc cancels.
 */
class InteractivePrompterIntegrationTest {

    private static final List<Prompter.Choice> CHOICES = List.of(
            new Prompter.Choice("OpenAI", "openai"),
            new Prompter.Choice("OpenRouter", "openrouter"),
            new Prompter.Choice("Anthropic", "anthropic"));

    @Test
    void typesToFilterAndSelectsWithEnter() throws Exception {
        var out = new ByteArrayOutputStream();
        byte[] input = "op\n".getBytes(StandardCharsets.UTF_8);
        Terminal terminal = TerminalBuilder.builder()
                .streams(new ByteArrayInputStream(input), out)
                .system(false)
                .build();
        var sut = new InteractivePrompter(terminal, () -> List.of());

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).hasValue("openai");
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("[filter: op]");
    }

    @Test
    void escCancelsSelection() throws Exception {
        var out = new ByteArrayOutputStream();
        byte[] input = new byte[]{27, 0};
        Terminal terminal = TerminalBuilder.builder()
                .streams(new ByteArrayInputStream(input), out)
                .system(false)
                .build();
        var sut = new InteractivePrompter(terminal, () -> List.of());

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoChoices() throws Exception {
        var out = new ByteArrayOutputStream();
        Terminal terminal = TerminalBuilder.builder()
                .streams(new ByteArrayInputStream(new byte[0]), out)
                .system(false)
                .build();
        var sut = new InteractivePrompter(terminal, () -> List.of());

        var result = sut.pick("Provider", List.of());

        assertThat(result).isEmpty();
    }
}
