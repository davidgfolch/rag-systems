package com.rag.tui.ui;

import com.rag.tui.help.ChatHelpText;
import com.rag.tui.help.DocumentHelpText;
import com.rag.tui.help.SessionHelpText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandRegistry {

    private static final String MODULE_ARG = "<module>";
    private static final String CONNECT = "connect";
    private static final List<CommandDescriptor> CONNECT_HELP = List.of(
            new CommandDescriptor("connect catalog", "browse the model catalog", "[<provider>]"),
            new CommandDescriptor("connect chat", "switch the chat model", "<provider> <model>"),
            new CommandDescriptor("connect embedding", "switch the embedding model", "<provider> <model>"),
            new CommandDescriptor("connect refresh", "re-fetch the model catalog", "")
    );
    private static final List<CommandDescriptor> COMMANDS = List.of(
            new CommandDescriptor("help", "show grouped command help or detail one command", "[<command>]"),
            new CommandDescriptor("modules", "list rag-* modules and their state", ""),
            new CommandDescriptor("use", "switch the active module", MODULE_ARG),
            new CommandDescriptor("start", "start a module as a child process", MODULE_ARG),
            new CommandDescriptor("stop", "stop a running module", MODULE_ARG),
            new CommandDescriptor("documents", "list ingested documents", ""),
            new CommandDescriptor("delete", "delete a document by id", "<document-id>"),
            new CommandDescriptor("add-file", "ingest a local file", "<path>"),
            new CommandDescriptor("add-folder", "ingest a directory recursively", "<path>"),
            new CommandDescriptor("add-url", "crawl a site from a URL (asks per page)",
                    "<url> [--max-pages N] [--all] [--no-discover]"),
            new CommandDescriptor("add-url-cancel", "stop the running crawl", ""),
            new CommandDescriptor("ask", "ask a question and stream the answer", "<question>"),
            new CommandDescriptor("history", "list past conversations", ""),
            new CommandDescriptor(CONNECT, "browse models or switch chat/embedding",
                    "[catalog [<provider>] | chat <provider> <model> | embedding <provider> <model> | refresh]"),
            new CommandDescriptor("quit", "exit the terminal", ""),
            new CommandDescriptor("variant", "list, create or activate pipeline variants",
                    "[<variant-id> | add [<strategy>] [<size>] [<overlap>]]")
    );
    private static final Map<String, List<String>> GROUPS = groups();
    private static final Map<String, String> DETAILS = details();
    private static final Map<String, CommandDescriptor> BY_NAME = COMMANDS.stream()
            .collect(Collectors.toMap(CommandDescriptor::name, command -> command));

    private static Map<String, List<String>> groups() {
        var groups = new LinkedHashMap<String, List<String>>();
        groups.put("Modules", List.of("modules", "use", "start", "stop"));
        groups.put("Documents", List.of("add-file", "add-folder", "add-url", "add-url-cancel",
                "documents", "delete"));
        groups.put("Chat", List.of("ask", "history"));
        groups.put("Providers", List.of(CONNECT));
        groups.put("Pipeline variants", List.of("variant"));
        groups.put("Session", List.of("help", "quit"));
        return groups;
    }

    private static Map<String, String> details() {
        var details = new LinkedHashMap<String, String>();
        details.putAll(SessionHelpText.details());
        details.putAll(DocumentHelpText.details());
        details.putAll(ChatHelpText.details());
        return details;
    }

    public List<CommandDescriptor> all() {
        return COMMANDS;
    }

    public Optional<CommandDescriptor> findByName(String name) {
        return Optional.ofNullable(BY_NAME.get(name.toLowerCase(Locale.ROOT)));
    }

    public List<CommandDescriptor> filter(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return COMMANDS.stream()
                .filter(c -> c.name().toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    public String generateUsage() {
        var rows = helpRows();
        int width = rows.stream().mapToInt(row -> row.label().length()).max().orElse(0);
        var sb = new StringBuilder("Available commands:").append(System.lineSeparator());
        String currentGroup = null;
        for (HelpRow row : rows) {
            if (!row.group().equals(currentGroup)) {
                currentGroup = row.group();
                sb.append(System.lineSeparator()).append(row.group()).append(System.lineSeparator());
            }
            sb.append("  ").append(row.label()).append(" ".repeat(width - row.label().length()))
                    .append("  ").append(row.description()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * Detailed help for one command: usage lines, arguments, recommended values
     * and behaviour notes. Unknown names fall back to the grouped overview.
     */
    public String generateCommandUsage(String arg) {
        String key = normalize(arg);
        if (key.isEmpty()) {
            return generateUsage();
        }
        String detail = DETAILS.get(key);
        if (detail == null) {
            detail = DETAILS.get(key.split(" ")[0]);
        }
        if (detail == null) {
            return "No help for '" + key + "'. Type 'help' to see the command list.";
        }
        var command = findByName(key.split(" ")[0]).orElseThrow();
        return command.name() + " - " + command.description()
                + System.lineSeparator() + System.lineSeparator() + detail;
    }

    private static String normalize(String arg) {
        return arg == null ? "" : arg.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static List<HelpRow> helpRows() {
        return GROUPS.entrySet().stream()
                .flatMap(group -> group.getValue().stream()
                        .flatMap(name -> rows(group.getKey(), name, BY_NAME.get(name))))
                .toList();
    }

    private static Stream<HelpRow> rows(String group, String name, CommandDescriptor command) {
        if (CONNECT.equals(name)) {
            return CONNECT_HELP.stream().map(sub -> new HelpRow(group, label(sub), sub.description()));
        }
        return Stream.of(new HelpRow(group, label(command), command.description()));
    }

    private static String label(CommandDescriptor command) {
        return command.usage().isEmpty() ? command.name() : command.name() + " " + command.usage();
    }

    private record HelpRow(String group, String label, String description) {
    }
}
