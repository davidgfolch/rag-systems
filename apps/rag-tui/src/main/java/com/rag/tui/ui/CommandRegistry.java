package com.rag.tui.ui;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CommandRegistry {

    private static final String MODULE_ARG = "<module>";
    private static final List<CommandDescriptor> COMMANDS = List.of(
            new CommandDescriptor("help", "show this help", ""),
            new CommandDescriptor("modules", "list known rag-* modules", ""),
            new CommandDescriptor("use", "switch the active module", MODULE_ARG),
            new CommandDescriptor("start", "start a module as a child process", MODULE_ARG),
            new CommandDescriptor("stop", "stop a running module", MODULE_ARG),
            new CommandDescriptor("documents", "list ingested documents", ""),
            new CommandDescriptor("add-file", "ingest a local document", "<path>"),
            new CommandDescriptor("add-url", "ingest a web page", "<url>"),
            new CommandDescriptor("ask", "stream a chat answer", "<question>"),
            new CommandDescriptor("history", "show conversation history", ""),
            new CommandDescriptor("quit", "exit the terminal", "")
    );

    public List<CommandDescriptor> all() {
        return COMMANDS;
    }

    public Optional<CommandDescriptor> findByName(String name) {
        return COMMANDS.stream()
                .filter(c -> c.name().equalsIgnoreCase(name))
                .findFirst();
    }

    public List<CommandDescriptor> filter(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return COMMANDS.stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    public String generateUsage() {
        var sb = new StringBuilder("Available commands:").append(System.lineSeparator());
        for (CommandDescriptor cmd : COMMANDS) {
            String args = cmd.usage().isEmpty() ? "" : " " + cmd.usage();
            sb.append(String.format("  /%-12s %s%n", cmd.name() + args, cmd.description()));
        }
        sb.append(System.lineSeparator()).append("Type / to browse commands, or enter a command directly.");
        return sb.toString();
    }
}
