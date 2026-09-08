package com.rag.tui.ui;

import java.util.List;
import java.util.Optional;

/**
 * Interactive terminal prompt contract. The concrete implementation may be a
 * filterable list over a real terminal (JLine) or a plain line reader in tests.
 */
public interface Prompter {

    /** A selectable list entry: a display label, a stable value, and optional detail text. */
    record Choice(String label, String value, String detail) {
        public Choice(String label, String value) {
            this(label, value, "");
        }
    }

    /**
     * Shows a typed-to-filter single-select list. Returns the chosen value, or
     * empty when the user cancels with Esc.
     */
    Optional<String> pick(String title, List<Choice> choices);

    /** Reads a free-text line, returning the trimmed input (possibly empty). */
    String prompt(String promptText);
}
