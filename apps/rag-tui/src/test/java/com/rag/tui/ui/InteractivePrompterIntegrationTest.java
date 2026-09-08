package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.rag.tui.ui.Key.KeyType.DOWN;
import static com.rag.tui.ui.Key.KeyType.ENTER;
import static com.rag.tui.ui.Key.KeyType.ESC;
import static com.rag.tui.ui.Key.KeyType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link InteractivePrompter#pick(String, List)} end-to-end through an
 * injected, scripted {@link InteractivePrompter.PickSource}. The selection loop
 * is real; only the key stream is scripted, so the test is deterministic and
 * needs no live terminal.
 */
class InteractivePrompterIntegrationTest {

    private static final List<Prompter.Choice> CHOICES = List.of(
            new Prompter.Choice("OpenAI", "openai"),
            new Prompter.Choice("OpenRouter", "openrouter"),
            new Prompter.Choice("Anthropic", "anthropic"));

    @Test
    void typesToFilterAndSelectsWithEnter() throws IOException {
        var sink = new StringBuilder();
        var sut = new InteractivePrompter(
                keys(typeChar('o'), typeChar('p'), key(ENTER)),
                sink::append, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).hasValue("openai");
        assertThat(sink).contains("[filter: op]");
    }

    @Test
    void filterThenNavigateDownSelectsSecondVisible() throws IOException {
        var sink = new StringBuilder();
        var sut = new InteractivePrompter(
                keys(typeChar('o'), key(DOWN), key(ENTER)),
                sink::append, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).hasValue("openrouter");
        assertThat(sink).contains("> OpenRouter");
    }

    @Test
    void escapeCancelsSelection() throws IOException {
        var sut = new InteractivePrompter(keys(key(ESC)), s -> { }, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoChoices() throws IOException {
        var sut = new InteractivePrompter(keys(key(ENTER)), s -> { }, 8);

        var result = sut.pick("Provider", List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void abortsWhenSourceEndsMidSelection() throws IOException {
        var sut = new InteractivePrompter(keys(typeChar('a')), s -> { }, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).isEmpty();
    }

    private static InteractivePrompter.PickSource keys(Key... keys) {
        return new InteractivePrompter.PickSource() {
            int i = 0;

            @Override
            public Key read() throws IOException {
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
