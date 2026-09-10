package com.rag.tui.ui;

import java.io.IOException;
import java.io.Writer;

public class InteractiveShell {

    private final CommandDispatcher dispatcher;
    private final Prompter prompter;
    private final Writer writer;

    public InteractiveShell(CommandDispatcher dispatcher, Prompter prompter, Writer writer) {
        this.dispatcher = dispatcher;
        this.prompter = prompter;
        this.writer = writer;
    }

    public void run() {
        try {
            write(TerminalStyle.welcome("RAG TUI - type 'help' for commands, 'quit' to exit"));
            writeProviderStatus();
            promptLoop();
        } catch (IOException e) {
            throw new ShellException("Terminal I/O error", e);
        }
    }

    private void promptLoop() throws IOException {
        String line;
        while ((line = prompter.prompt("> ")) != null) {
            if (line.isEmpty()) continue;
            if (runCommand(line)) return;
        }
    }

    private void writeProviderStatus() throws IOException {
        String summary = dispatcher.providerSummary();
        if (summary != null && !summary.isEmpty()) {
            write(TerminalStyle.info("Providers:\n" + summary));
        }
    }

    private boolean runCommand(String line) throws IOException {
        try {
            write(dispatcher.handle(line, this::writeToken));
            return false;
        } catch (ShellExitException e) {
            write(TerminalStyle.success("Bye."));
            return true;
        } catch (RuntimeException e) {
            write(TerminalStyle.error("Error: " + e.getMessage()));
            return false;
        }
    }

    private void write(String text) throws IOException {
        writer.write(decorate(text));
        writer.write(System.lineSeparator());
        writer.flush();
    }

    private void writeToken(String token) {
        try {
            writer.write(decorate(token));
            writer.flush();
        } catch (IOException e) {
            throw new ShellException("Terminal I/O error", e);
        }
    }

    private static String decorate(String text) {
        return text.indexOf('\u001B') >= 0 ? text : TerminalStyle.response(text);
    }

    public static class ShellException extends RuntimeException {
        public ShellException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
