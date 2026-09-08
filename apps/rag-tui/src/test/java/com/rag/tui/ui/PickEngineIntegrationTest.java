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
 * Drives {@link PickEngine}'s filter + navigation + select cycle end-to-end
 * through a real {@link InteractivePrompter} over a piped JLine terminal.
 */
class PickEngineIntegrationTest {

    private static final List<Prompter.Choice> CHOICES = List.of(
            new Prompter.Choice("OpenAI", "openai"),
            new Prompter.Choice("OpenRouter", "openrouter"),
            new Prompter.Choice("Anthropic", "anthropic"));

    @Test
    void filtersThenNavigatesAndSelects() throws Exception {
        var out = new ByteArrayOutputStream();
        byte[] input = "oj\n".getBytes(StandardCharsets.UTF_8);
        Terminal terminal = TerminalBuilder.builder()
                .streams(new ByteArrayInputStream(input), out)
                .system(false)
                .build();
        var prompter = new InteractivePrompter(terminal, () -> List.of());

        var result = prompter.pick("Provider", CHOICES);

        assertThat(result).hasValue("openrouter");
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("> OpenRouter");
    }
}
