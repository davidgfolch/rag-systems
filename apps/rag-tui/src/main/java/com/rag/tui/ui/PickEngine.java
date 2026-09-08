package com.rag.tui.ui;

import java.util.List;
import java.util.Locale;

/**
 * Pure filterable single-select state machine, independent of any terminal
 * I/O so it can be unit-tested without a real JLine {@code Terminal}.
 */
final class PickEngine {

    private final List<Prompter.Choice> all;
    private String filter = "";
    private int cursor = 0;

    PickEngine(List<Prompter.Choice> choices) {
        this.all = List.copyOf(choices);
    }

    List<Prompter.Choice> visible() {
        String needle = filter.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(c -> c.label().toLowerCase(Locale.ROOT).startsWith(needle)
                        || c.value().toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
    }

    void append(char ch) {
        filter += ch;
        cursor = 0;
    }

    void backspace() {
        if (!filter.isEmpty()) {
            filter = filter.substring(0, filter.length() - 1);
            cursor = 0;
        }
    }

    void next() {
        var size = visible().size();
        if (size > 0) cursor = (cursor + 1) % size;
    }

    void prev() {
        var size = visible().size();
        if (size > 0) cursor = (cursor - 1 + size) % size;
    }

    int cursor() {
        return cursor;
    }

    String filter() {
        return filter;
    }
}
