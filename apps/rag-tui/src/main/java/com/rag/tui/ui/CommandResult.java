package com.rag.tui.ui;

/**
 * Result of handling a single command line in the terminal: text to print and
 * whether the terminal should exit.
 */
public record CommandResult(String message, boolean exit) {}
