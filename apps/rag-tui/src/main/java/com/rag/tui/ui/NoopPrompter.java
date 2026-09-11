package com.rag.tui.ui;

import java.util.List;
import java.util.Optional;

/**
 * A no-op {@link Prompter} for non-interactive input (pipes, tests): reads a
 * plain line for {@link #prompt} and never shows a picker, returning empty.
 */
public class NoopPrompter implements Prompter {

    private final java.io.Reader reader;

    public NoopPrompter(java.io.Reader reader) {
        this.reader = reader instanceof java.io.BufferedReader b ? b : new java.io.BufferedReader(reader);
    }

    @Override
    public Optional<String> pick(String title, List<Choice> choices) {
        return Optional.empty();
    }

    @Override
    public String prompt(String promptText) {
        try {
            return ((java.io.BufferedReader) reader).readLine();
        } catch (java.io.IOException _) {
            return null;
        }
    }
}
