package com.rag.tui.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class InteractiveShell {

    private final CommandDispatcher dispatcher;
    private final BufferedReader reader;
    private final Writer writer;
    private final CommandPicker picker;

    public InteractiveShell(CommandDispatcher dispatcher, Reader reader, Writer writer, CommandPicker picker) {
        this.dispatcher = dispatcher;
        this.reader = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        this.writer = writer;
        this.picker = picker;
    }

    public void run() {
        try {
            write(TerminalStyle.welcome("RAG TUI - type 'help' for commands, 'quit' to exit, '/' to browse commands"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (runLine(line)) break;
            }
        } catch (IOException e) {
            throw new ShellException("Terminal I/O error", e);
        }
    }

    private boolean runLine(String line) throws IOException {
        String trimmed = line.trim();
        if (trimmed.startsWith("/")) {
            return runPicker(trimmed.substring(1).trim());
        }
        return runCommand(trimmed);
    }

    private boolean runPicker(String filter) throws IOException {
        if (picker == null) return runCommand("/" + filter);
        CommandDescriptor selected = picker.pick(filter);
        if (selected == null) return false;
        write(TerminalStyle.info("/" + selected.name()));
        return runCommand("/" + selected.name());
    }

    private boolean runCommand(String line) throws IOException {
        try {
            CommandResult result = dispatcher.handle(line, this::writeToken);
            write(result.message());
            return result.exit();
        } catch (RuntimeException e) {
            write(TerminalStyle.error("Error: " + e.getMessage()));
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
