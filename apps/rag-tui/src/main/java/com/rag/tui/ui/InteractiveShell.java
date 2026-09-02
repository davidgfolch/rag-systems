package com.rag.tui.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Interactive terminal loop: reads command lines, dispatches them, and prints
 * results until the user quits. Input/output are injected for testability.
 */
public class InteractiveShell {

    private final CommandDispatcher dispatcher;
    private final BufferedReader reader;
    private final Writer writer;

    public InteractiveShell(CommandDispatcher dispatcher, Reader reader, Writer writer) {
        this.dispatcher = dispatcher;
        this.reader = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        this.writer = writer;
    }

    public void run() {
        try {
            write("RAG TUI - type 'help' for commands, 'quit' to exit\n");
            String line;
            while ((line = reader.readLine()) != null) {
                if (runCommand(line)) break;
            }
        } catch (IOException e) {
            throw new ShellException("Terminal I/O error", e);
        }
    }

    private boolean runCommand(String line) throws IOException {
        try {
            CommandResult result = dispatcher.handle(line, this::writeToken);
            write(result.message());
            return result.exit();
        } catch (RuntimeException e) {
            write("Error: " + e.getMessage());
            return false;
        }
    }

    private void write(String text) throws IOException {
        writer.write(text);
        writer.write(System.lineSeparator());
        writer.flush();
    }

    private void writeToken(String token) {
        try {
            writer.write(token);
            writer.flush();
        } catch (IOException e) {
            throw new ShellException("Terminal I/O error", e);
        }
    }

    public static class ShellException extends RuntimeException {
        public ShellException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
