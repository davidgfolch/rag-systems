package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineEditorTest {

    private static final CompletionCandidates MODULES =
            (line, cursor) -> List.of("rag-basic", "rag-advanced", "rag-agentic");

    /** Filters candidates down by the current (last) token. */
    private static final CompletionCandidates PREFIX =
            (line, cursor) -> {
                String prefix = com.rag.tui.ui.CommandCompletion.currentToken(line.substring(0, cursor));
                return List.of("openai", "openrouter", "ollama").stream()
                        .filter(c -> c.startsWith(prefix.toLowerCase()))
                        .toList();
            };

    private final LineEditor sut = new LineEditor();

    @Test
    void showsPopupWhenTypingMatchesCandidates() {
        sut.setCompletion(PREFIX);
        sut.start();

        sut.type('o');

        assertThat(sut.popupVisible()).isTrue();
        assertThat(sut.popup()).containsExactly("openai", "openrouter", "ollama");
    }

    @Test
    void filtersPopupAsMoreTyped() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('o');
        sut.type('p');

        assertThat(sut.popup()).containsExactly("openai", "openrouter");
    }

    @Test
    void arrowDownMovesPopupCursor() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('o');

        sut.accept(new Key(Key.KeyType.DOWN, ' '));

        assertThat(sut.popupCursor()).isEqualTo(1);
    }

    @Test
    void enterSelectsPopupEntryAndClosesPopup() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('o');

        boolean moved = sut.selectPopup();

        assertThat(moved).isTrue();
        assertThat(sut.text()).isEqualTo("openai");
        assertThat(sut.popupVisible()).isFalse();
    }

    @Test
    void enterWhenNoPopupSubmitsLine() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('a');
        sut.type('b');
        sut.accept(new Key(Key.KeyType.ESC, ' '));

        boolean submitted = sut.accept(new Key(Key.KeyType.ENTER, ' '));

        assertThat(submitted).isTrue();
        assertThat(sut.submitted()).isEqualTo("ab");
        assertThat(sut.text()).isEmpty();
    }

    @Test
    void hidePopupWhenTokenExactlyMatchesSingleCandidate() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('o');
        sut.type('p');
        sut.type('e');
        sut.type('n');
        sut.type('a');
        sut.type('i');

        assertThat(sut.popupVisible()).isFalse();
        assertThat(sut.text()).isEqualTo("openai");
    }

    @Test
    void escapeDismissesPopup() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('o');
        assertThat(sut.popupVisible()).isTrue();

        sut.accept(new Key(Key.KeyType.ESC, ' '));
        sut.accept(new Key(Key.KeyType.ENTER, ' '));

        assertThat(sut.text()).isEmpty();
    }

    @Test
    void backspaceRefreshesPopup() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('o');
        sut.type('p');

        sut.accept(new Key(Key.KeyType.BACKSPACE, ' '));

        assertThat(sut.popup()).containsExactly("openai", "openrouter", "ollama");
    }

    @Test
    void historyNavigationWhenNoPopup() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('h');
        sut.accept(new Key(Key.KeyType.ENTER, ' '));
        sut.start();

        sut.accept(new Key(Key.KeyType.UP, ' '));

        assertThat(sut.text()).isEqualTo("h");
    }

    @Test
    void popupUpWrapsToLastEntry() {
        sut.setCompletion(PREFIX);
        sut.start();
        sut.type('o');

        sut.accept(new Key(Key.KeyType.UP, ' '));

        assertThat(sut.popupCursor()).isEqualTo(2);
    }

    @Test
    void moduleCompletionPopulatesModuleNames() {
        sut.setCompletion(MODULES);
        sut.start();
        sut.type('u');
        sut.type('s');
        sut.type('e');
        sut.type(' ');

        assertThat(sut.popup()).containsExactly("rag-basic", "rag-advanced", "rag-agentic");
    }
}