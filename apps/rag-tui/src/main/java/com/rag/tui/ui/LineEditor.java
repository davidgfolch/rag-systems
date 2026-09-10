package com.rag.tui.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure state machine for a single editable input line with an auto-complete
 * popup. Independent of any terminal I/O so it can be unit-tested without a
 * live {@code Terminal}. The interactive loop ({@link InteractivePrompter})
 * feeds decoded {@link Key}s into it and renders its {@link View}.
 */
final class LineEditor {

    private static final char SPACE = ' ';

    private final StringBuilder buffer = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private int cursor = 0;
    private int historyIndex = -1;
    private CompletionCandidates candidates;
    private List<String> popup = List.of();
    private int popupCursor = 0;
    private String lastSubmitted = "";

    void start() {
        buffer.setLength(0);
        cursor = 0;
        popup = List.of();
        lastSubmitted = "";
        historyIndex = history.size();
    }

    void setCompletion(CompletionCandidates candidates) {
        this.candidates = candidates;
    }

    /** Processes a decoded key. Returns {@code true} when the line is submitted (Enter). */
    boolean accept(Key key) {
        return switch (key.type()) {
            case ENTER -> {
                submit();
                yield true;
            }
            case ESC -> {
                closePopup();
                yield false;
            }
            case BACKSPACE -> {
                backspace();
                yield false;
            }
            case TYPE -> {
                type(key.value());
                yield false;
            }
            case UP -> {
                if (popupVisible()) {
                    popupUp();
                } else {
                    historyUp();
                }
                yield false;
            }
            case DOWN -> {
                if (popupVisible()) {
                    popupDown();
                } else {
                    historyDown();
                }
                yield false;
            }
            case LEFT -> {
                if (cursor > 0) {
                    cursor--;
                    refresh();
                }
                yield false;
            }
            case RIGHT -> {
                if (cursor < buffer.length()) {
                    cursor++;
                    refresh();
                }
                yield false;
            }
            case NONE -> false;
        };
    }

    /** Applies a typed character, then refreshes the popup. */
    void type(char ch) {
        buffer.insert(cursor, ch);
        cursor++;
        refresh();
    }

    void backspace() {
        if (cursor == 0) return;
        buffer.deleteCharAt(cursor - 1);
        cursor--;
        refresh();
    }

    private void historyUp() {
        if (historyIndex > 0) {
            historyIndex--;
            applyHistory();
        }
    }

    private void historyDown() {
        if (historyIndex < history.size()) {
            historyIndex++;
            applyHistory();
        }
    }

    private void applyHistory() {
        String entry = historyIndex < history.size() ? history.get(historyIndex) : "";
        buffer.setLength(0);
        buffer.append(entry);
        cursor = buffer.length();
        refresh();
    }

    private void popupDown() {
        if (popup.isEmpty()) return;
        popupCursor = (popupCursor + 1) % popup.size();
    }

    private void popupUp() {
        if (popup.isEmpty()) return;
        popupCursor = (popupCursor - 1 + popup.size()) % popup.size();
    }

    /** Selects the highlighted popup entry, inserting it and closing the popup. */
    boolean selectPopup() {
        if (popup.isEmpty()) return false;
        String selected = popup.get(popupCursor);
        int start = tokenStart();
        buffer.replace(start, cursor, selected);
        cursor = start + selected.length();
        refresh();
        return true;
    }

    void closePopup() {
        popup = List.of();
    }

    private void submit() {
        String line = buffer.toString();
        buffer.setLength(0);
        cursor = 0;
        popup = List.of();
        if (!line.isBlank()) {
            history.add(line);
            lastSubmitted = line;
        }
        historyIndex = history.size();
    }

    private void refresh() {
        if (candidates == null || buffer.isEmpty()) {
            popup = List.of();
            return;
        }
        popup = candidates.candidates(buffer.toString(), cursor);
        String token = buffer.substring(tokenStart(), cursor);
        if (popup.size() == 1 && token.equals(popup.get(0))) {
            popup = List.of();
        }
        popupCursor = 0;
    }

    /** Index of the start of the token under the cursor (the last space-delimited run). */
    private int tokenStart() {
        int start = cursor;
        while (start > 0 && buffer.charAt(start - 1) != SPACE) {
            start--;
        }
        return start;
    }

    boolean popupVisible() {
        return !popup.isEmpty();
    }

    String text() {
        return buffer.toString();
    }

    String submitted() {
        return lastSubmitted;
    }

    List<String> popup() {
        return popup;
    }

    int popupCursor() {
        return popupCursor;
    }

    /** Visual state rendered by the terminal: the line plus optionally the popup. */
    record View(String line, int cursor, List<String> popup, int popupCursor, boolean popupVisible) {

        static View line(String line, int cursor) {
            return new View(line, cursor, List.of(), 0, false);
        }
    }

    View view() {
        return new View(buffer.toString(), cursor, popup, popupCursor, popupVisible());
    }
}
