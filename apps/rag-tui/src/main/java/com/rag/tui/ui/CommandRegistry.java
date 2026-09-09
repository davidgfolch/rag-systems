package com.rag.tui.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CommandRegistry {

    private static final String MODULE_ARG = "<module>";
    private static final List<CommandDescriptor> CONNECT_HELP = List.of(
            new CommandDescriptor("connect catalog", "browse the model catalog via rag-provider", "[<provider>]"),
            new CommandDescriptor("connect chat", "switch the chat model via rag-provider", "<provider> <model>"),
            new CommandDescriptor("connect embedding", "switch the embedding model via rag-provider", "<provider> <model>"),
            new CommandDescriptor("connect refresh", "re-fetch the model catalog", "")
    );
    private static final List<CommandDescriptor> COMMANDS = List.of(
            new CommandDescriptor("help", "show this help", ""),
            new CommandDescriptor("modules", "list known rag-* modules", ""),
            new CommandDescriptor("use", "switch the active module", MODULE_ARG),
            new CommandDescriptor("start", "start a module as a child process", MODULE_ARG),
            new CommandDescriptor("stop", "stop a running module", MODULE_ARG),
            new CommandDescriptor("documents", "list ingested documents", ""),
            new CommandDescriptor("delete", "delete an ingested document", "<document-id>"),
            new CommandDescriptor("add-file", "ingest a local document", "<path>"),
            new CommandDescriptor("add-folder", "ingest all files in a directory (recursive)", "<path>"),
            new CommandDescriptor("add-url", "ingest a web page", "<url>"),
            new CommandDescriptor("ask", "stream a chat answer", "<question>"),
            new CommandDescriptor("history", "show conversation history", ""),
            new CommandDescriptor("connect", "browse the model catalog and switch chat/embedding models via rag-provider",
                    "[catalog [<provider>] | chat <provider> <model> | embedding <provider> <model> | refresh]"),
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
        List<String[]> rows = new ArrayList<>();
        for (CommandDescriptor cmd : COMMANDS) {
            if (cmd.name().equals("connect")) {
                for (CommandDescriptor sub : CONNECT_HELP) {
                    rows.add(new String[]{sub.name() + argsOf(sub.usage()), sub.description()});
                }
            } else {
                rows.add(new String[]{cmd.name() + argsOf(cmd.usage()), cmd.description()});
            }
        }
        int width = rows.stream().mapToInt(r -> r[0].length()).max().orElse(0);
        var sb = new StringBuilder("Available commands:").append(System.lineSeparator());
        for (String[] row : rows) {
            sb.append(String.format("  %-" + width + "s  %s%n", row[0], row[1]));
        }
        return sb.toString();
    }

    private static String argsOf(String usage) {
        return usage.isEmpty() ? "" : " " + usage;
    }
}
