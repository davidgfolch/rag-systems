package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.rag.tui.ui.Key.KeyType.DOWN;
import static com.rag.tui.ui.Key.KeyType.ENTER;
import static com.rag.tui.ui.Key.KeyType.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link LineEditor} through the real auto-complete loop of
 * {@link InteractivePrompter#prompt(String)}: a scripted
 * {@link InteractivePrompter.PickSource} feeds keys; the popup renders into a
 * sink, exactly as it would on a live terminal.
 */
class LineEditorIntegrationTest {

    private static final CompletionCandidates PROVIDERS = (line, cursor) -> {
        String token = CommandCompletion.currentToken(line.substring(0, cursor));
        return List.of("openai", "openrouter", "ollama").stream()
                .filter(c -> c.contains(token.toLowerCase()))
                .toList();
    };

    @Test
    void typingOpensPopupAndRendersCandidates() throws IOException {
        var sink = new StringBuilder();
        var sut = new InteractivePrompter(
                keys(typeChar('o')),
                sink::append, 8, PROVIDERS);

        sut.prompt("> ");

        assertThat(sink).contains("openai").contains("openrouter").contains("ollama");
    }

    @Test
    void arrowDownAndEnterSelectsPopupEntry() throws IOException {
        var sut = new InteractivePrompter(
                keys(typeChar('o'), key(DOWN), key(ENTER), key(ENTER)),
                s -> { }, 8, PROVIDERS);

        var result = sut.prompt("> ");

        assertThat(result).isEqualTo("openrouter");
    }

    @Test
    void plainEnterSubmitsWithoutPopup() throws IOException {
        var sut = new InteractivePrompter(
                keys(typeChar('h'), typeChar('i'), key(ENTER)),
                s -> { }, 8, PROVIDERS);

        var result = sut.prompt("> ");

        assertThat(result).isEqualTo("hi");
    }

    @Test
    void escapeClosesPopupAndEnterSubmits() throws IOException {
        var sut = new InteractivePrompter(
                keys(typeChar('o'), key(Key.KeyType.ESC), key(ENTER)),
                s -> { }, 8, PROVIDERS);

        var result = sut.prompt("> ");

        assertThat(result).isEqualTo("o");
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