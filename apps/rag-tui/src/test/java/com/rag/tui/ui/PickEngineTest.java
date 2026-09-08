package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PickEngineTest {

    private static final List<Prompter.Choice> CHOICES = List.of(
            new Prompter.Choice("OpenAI", "openai"),
            new Prompter.Choice("OpenRouter", "openrouter"),
            new Prompter.Choice("Anthropic", "anthropic"),
            new Prompter.Choice("Ollama", "ollama"));

    @Test
    void filtersByLabelOrValueWhileTyping() {
        var engine = new PickEngine(CHOICES);

        engine.append('o');
        engine.append('p');

        assertThat(engine.visible()).extracting(Prompter.Choice::value)
                .containsExactly("openai", "openrouter");
    }

    @Test
    void clearsFilterToShowAll() {
        var engine = new PickEngine(CHOICES);
        engine.append('o');
        engine.append('p');
        engine.backspace();
        engine.backspace();

        assertThat(engine.visible()).hasSize(CHOICES.size());
    }

    @Test
    void nextAndPrevCycleWithinVisible() {
        var engine = new PickEngine(CHOICES);
        engine.append('o');
        engine.append('p'); // openai, openrouter

        engine.next();
        assertThat(engine.visible().get(engine.cursor()).value()).isEqualTo("openrouter");
        engine.next();
        assertThat(engine.visible().get(engine.cursor()).value()).isEqualTo("openai");
        engine.prev();
        assertThat(engine.visible().get(engine.cursor()).value()).isEqualTo("openrouter");
    }

    @Test
    void emptyFilterMatchesEverythingAndStartsAtFirst() {
        var engine = new PickEngine(CHOICES);

        assertThat(engine.visible()).hasSize(CHOICES.size());
        assertThat(engine.cursor()).isZero();
    }

    @Test
    void returnsNoVisiblesWhenNothingMatches() {
        var engine = new PickEngine(CHOICES);
        for (char c : "zzz".toCharArray()) engine.append(c);

        assertThat(engine.visible()).isEmpty();
    }
}
