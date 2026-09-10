package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.tui.ui.Key.KeyType.DOWN;
import static com.rag.tui.ui.Key.KeyType.ENTER;
import static com.rag.tui.ui.Key.KeyType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link InteractivePrompter#pick(String, List)} through the real
 * selection loop + {@link PickEngine} (filter + navigation + select), using a
 * scripted, deterministic {@link InteractivePrompter.PickSource} so it runs
 * anywhere without a live terminal.
 */
class PickEngineIntegrationTest {

    private static final List<Prompter.Choice> CHOICES = List.of(
            new Prompter.Choice("OpenAI", "openai"),
            new Prompter.Choice("OpenRouter", "openrouter"),
            new Prompter.Choice("Anthropic", "anthropic"));

    @Test
    void filtersThenNavigatesAndSelects() {
        var sink = new StringBuilder();
        var sut = new InteractivePrompter(
                keys(typeChar('o'), key(DOWN), key(ENTER)),
                sink::append, 8);
        var result = sut.pick("Provider", CHOICES);
        assertThat(result).hasValue("openrouter");
        assertThat(sink).contains("> OpenRouter");
    }

    private static InteractivePrompter.PickSource keys(Key... keys) {
        return new InteractivePrompter.PickSource() {
            int i = 0;

            @Override
            public Key read() {
                return i < keys.length ? keys[i++] : null;
            }
        };
    }

    private static Key typeChar(char c) {
        return new Key(TYPE, c);
    }

    private static Key key(Key.KeyType t) {
        return new Key(t, ' ');
    }
}