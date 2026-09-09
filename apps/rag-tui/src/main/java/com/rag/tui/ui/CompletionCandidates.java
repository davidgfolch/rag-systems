package com.rag.tui.ui;

import java.util.List;

/**
 * Supplies auto-completion candidates for a typed token. Implementations are
 * lazy: candidate sources (REST calls, registries) are evaluated only when the
 * token context needs them, and should swallow transient failures by returning
 * an empty list.
 */
public interface CompletionCandidates {

    /** Candidates for the given current line and cursor position, or empty for none. */
    List<String> candidates(String line, int cursor);
}
