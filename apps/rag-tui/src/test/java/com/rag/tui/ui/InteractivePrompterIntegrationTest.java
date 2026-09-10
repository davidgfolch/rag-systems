package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.tui.ui.Key.KeyType.DOWN;
import static com.rag.tui.ui.Key.KeyType.ENTER;
import static com.rag.tui.ui.Key.KeyType.ESC;
import static com.rag.tui.ui.Key.KeyType.LEFT;
import static com.rag.tui.ui.Key.KeyType.RIGHT;
import static com.rag.tui.ui.Key.KeyType.TYPE;
import static com.rag.tui.ui.Key.KeyType.UP;
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
    void typesToFilterAndSelectsWithEnter() {
        var sink = new StringBuilder();
        var sut = new InteractivePrompter(
                keys(typeChar('o'), typeChar('p'), key(ENTER)),
                sink::append, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).hasValue("openai");
        assertThat(sink).contains("[filter: op]");
    }

    @Test
    void filterThenNavigateDownSelectsSecondVisible() {
        var sink = new StringBuilder();
        var sut = new InteractivePrompter(
                keys(typeChar('o'), key(DOWN), key(ENTER)),
                sink::append, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).hasValue("openrouter");
        assertThat(sink).contains("> OpenRouter");
    }

    @Test
    void escapeCancelsSelection() {
        var sut = new InteractivePrompter(keys(key(ESC)), s -> { }, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoChoices() {
        var sut = new InteractivePrompter(keys(key(ENTER)), s -> { }, 8);

        var result = sut.pick("Provider", List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void abortsWhenSourceEndsMidSelection() {
        var sut = new InteractivePrompter(keys(typeChar('a')), s -> { }, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).isEmpty();
    }

    @Test
    void recallsPreviousSubmissionViaArrowUpOnNextPrompt() {
        var sut = new InteractivePrompter(
                keys(typeChar('h'), typeChar('i'), key(ENTER), key(UP), key(ENTER)),
                s -> { }, 8);

        assertThat(sut.prompt("> ")).isEqualTo("hi");
        assertThat(sut.prompt("> ")).isEqualTo("hi");
    }

    @Test
    void keepsSubmittedLineVisibleAndClearsOnlyPopupAreaOnSubmit() {
        var sink = new StringBuilder();
        var sut = new InteractivePrompter(keys(typeChar('h'), typeChar('i'), key(ENTER)), sink::append, 8);

        assertThat(sut.prompt("> ")).isEqualTo("hi");
        assertThat(sink).contains("hi");
        assertThat(sink.toString()).endsWith("\u001B[J\r\n");
        assertThat(sink.toString()).doesNotEndWith("\u001B[2K\u001B[J");
    }

    @Test
    void ignoresUnusedMovementKeysInPick() {
        var sink = new StringBuilder();
        var sut = new InteractivePrompter(
                keys(key(LEFT), key(RIGHT), typeChar('o'), key(ENTER)),
                sink::append, 8);

        var result = sut.pick("Provider", CHOICES);

        assertThat(result).hasValue("openai");
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
