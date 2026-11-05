package com.rag.tui.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class CommandRegistry {

    private static final String MODULE_ARG = "<module>";
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
            new CommandDescriptor("add-url", "crawl a site from a URL (asks per page)", "<url>"),
            new CommandDescriptor("add-url-cancel", "stop the running crawl", ""),
            new CommandDescriptor("ask", "ask a question and stream the answer", "<question>"),
            new CommandDescriptor("history", "list past conversations", ""),
            new CommandDescriptor("connect", "browse models or switch chat/embedding",
                    "[catalog [<provider>] | chat <provider> <model> | embedding <provider> <model> | refresh]"),
            new CommandDescriptor("quit", "exit the terminal", ""),
            new CommandDescriptor("variant", "list, create or activate pipeline variants",
                    "[<variant-id> | add [<strategy>] [<size>] [<overlap>]]")
    );
    private static final Map<String, List<String>> GROUPS = groups();
    private static final Map<String, String> DETAILS = details();

    private static Map<String, List<String>> groups() {
        var groups = new LinkedHashMap<String, List<String>>();
        groups.put("Modules", List.of("modules", "use", "start", "stop"));
        groups.put("Documents", List.of("add-file", "add-folder", "add-url", "add-url-cancel",
                "documents", "delete"));
        groups.put("Chat", List.of("ask", "history"));
        groups.put("Providers", List.of("connect"));
        groups.put("Pipeline variants", List.of("variant"));
        groups.put("Session", List.of("help", "quit"));
        return groups;
    }

    private static Map<String, String> details() {
        var details = new LinkedHashMap<String, String>();
        details.put("help", """
                Usage:
                  help              list every command, grouped by concept
                  help <command>    describe one command, its arguments and defaults

                Arguments:
                  <command>   command name without arguments, e.g. 'help variant'
                              ('help connect chat' also resolves to 'connect')

                Notes:
                  'help variant' documents the chunking strategies with their
                  recommended size/overlap values.
                """);
        details.put("modules", """
                Usage:
                  modules

                Output:
                  Every rag-* module with its state, whether the TUI started it as a
                  child process, an external process was found, and (active) for the
                  module the other commands currently target.
                """);
        details.put("use", """
                Usage:
                  use              pick the module interactively
                  use <module>     switch to <module>

                Arguments:
                  <module>   module name, e.g. rag-basic, rag-advanced, rag-agentic

                Notes:
                  Only one module is active at a time: add-file, add-folder, add-url,
                  ask, documents, delete and variant all target it. Switching does not
                  start or stop the module. The reply also shows the provider's active
                  chat and embedding models.
                """);
        details.put("start", """
                Usage:
                  start             pick a stopped module interactively
                  start <module>    start <module> as a child process

                Arguments:
                  <module>   module name, e.g. rag-basic

                Notes:
                  Waits for the module health probe before reporting success, so a
                  'Started ... (ready)' reply means the module accepts requests.
                  'Module already running' means it was started earlier or externally.
                """);
        details.put("stop", """
                Usage:
                  stop             pick a running module interactively
                  stop <module>    stop a module the TUI started as a child

                Arguments:
                  <module>   module name, e.g. rag-basic

                Notes:
                  Only child processes started with 'start' are stopped. An externally
                  started module is reported as 'Module not running'.
                """);
        details.put("documents", """
                Usage:
                  documents

                Output:
                  Documents of every reachable module, grouped per module, with
                  document id, chunk count, ingestion time and title. Use the id with
                  'delete'.
                """);
        details.put("delete", """
                Usage:
                  delete                 pick a document interactively
                  delete <document-id>   delete by id

                Arguments:
                  <document-id>   id shown by 'documents', e.g. file-<uuid>

                Notes:
                  Searches every reachable module, so the active module does not
                  matter. Deletion happens immediately and is not confirmed; re-ingest
                  the source to restore it.
                """);
        details.put("add-file", """
                Usage:
                  add-file           prompt for the path
                  add-file <path>    ingest one local file

                Arguments:
                  <path>   file path (relative or absolute); PDF, DOCX, TXT and HTML
                           are parsed by Tika when rag.parsing.mode=tika

                Notes:
                  The file is read, submitted and polled in the background, so the
                  shell stays usable while chunks are embedded. Identical content
                  (SHA-256) is detected first and you are asked whether to override;
                  an identical ingestion already in flight is skipped.
                """);
        details.put("add-folder", """
                Usage:
                  add-folder           prompt for the directory
                  add-folder <path>    ingest every supported file below <path>

                Arguments:
                  <path>   directory; its subdirectories are traversed recursively

                Notes:
                  Same parsing and content-hash deduplication as add-file, but
                  duplicates are skipped without asking. Each file is submitted
                  separately and reported as it completes.
                """);
        details.put("add-url", """
                Usage:
                  add-url           prompt for the URL
                  add-url <url>     start an interactive breadth-first crawl

                Arguments:
                  <url>   http(s) URL of the first page

                Notes:
                  The active module fetches and ranks pages; the TUI asks one question
                  per page to steer link ranking (blank = no guidance). After the
                  initial pages one "Go on?" question decides whether to crawl more.
                  Answer 'add-url-cancel' or 'cancel' at any prompt to stop. Pages are
                  ingested asynchronously and de-duplicated by domain + URI.
                """);
        details.put("add-url-cancel", """
                Usage:
                  add-url-cancel

                Notes:
                  Cancels the crawl currently being driven and prints how many pages
                  were submitted, skipped and how many links were filtered out. Safe
                  to run when nothing is crawling ("No URL crawl is active.").
                """);
        details.put("ask", """
                Usage:
                  ask              prompt for the question
                  ask <question>   ask the active module and stream the answer

                Arguments:
                  <question>   natural-language question; multi-word questions can be
                               typed as-is

                Notes:
                  Retrieves the top-k chunks (rag.chat.top-k, default 4) using the
                  active variant's embeddings, then streams the answer with its source
                  chunks. Each ask starts a new TUI conversation id (tui-<timestamp>);
                  use 'history' to see what rag-memory has recorded.
                """);
        details.put("history", """
                Usage:
                  history

                Output:
                  Every conversation recorded by rag-memory with its id, title and
                  message count.
                """);
        details.put("connect", """
                Usage:
                  connect                          show active models and catalog status
                  connect catalog [<provider>]     browse the catalog, optionally filtered
                  connect chat [<provider>] [<model>]       switch the chat model
                  connect embedding [<provider>] [<model>]  switch the embedding model
                  connect refresh                  re-fetch the model catalog

                Arguments:
                  <provider>   provider id, e.g. ollama or openai
                  <model>      model id printed by 'connect catalog'

                Notes:
                  Missing provider/model open a picker, so bare 'connect chat' works
                  too. The switch is runtime-only (no restart) and drives the sealed
                  'boot' variant; other variants keep the embedding model they were
                  created with. An unknown provider is offered for registration on the
                  spot.
                """);
        details.put("quit", """
                Usage:
                  quit      exit the terminal (alias: exit)
                """);
        details.put("variant", """
                Usage:
                  variant                        list variants and pick one to activate
                  variant <variant-id>           activate a variant by id
                  variant add                    create one interactively
                  variant add <strategy> [<size>] [<overlap>]
                                                 create one; missing values are prompted

                Arguments:
                  <variant-id>   id shown by 'variant', e.g. boot or v2
                  <strategy>     RECURSIVE, FIXED, TOKEN or SEMANTIC
                  <size>         chunk window: characters for RECURSIVE, FIXED and
                                 SEMANTIC, whitespace tokens for TOKEN; must be > 0
                  <overlap>      window repeated between chunks; 0 <= overlap < size

                Recommended values:
                  RECURSIVE 512/128   prose and mixed docs - start here
                  RECURSIVE 384/96    code and API reference pages
                  FIXED     512/128   homogeneous text only (logs, reviews)
                  TOKEN     256/64    strict token windows (size/4 must exceed overlap/4)
                  SEMANTIC  512/128   high-value documents; ~4.5x the embedding cost

                Notes:
                  Blank prompts keep the default shown in brackets, which is the active
                  variant's window (the seeded boot variant uses rag.chunking.size /
                  overlap). A new variant inherits the active embedding model and
                  dimension and stores its vectors in its own variant_<id> table, so
                  switching never mixes vectors and needs no restart. For SEMANTIC the
                  window is an upper bound: topic shifts cut earlier and oversized
                  sentences are hard-split.
                """);
        return details;
    }

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
        var rows = new ArrayList<HelpRow>();
        for (var group : GROUPS.entrySet()) {
            for (String name : group.getValue()) {
                var command = COMMANDS.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
                if ("connect".equals(name)) {
                    CONNECT_HELP.forEach(sub ->
                            rows.add(new HelpRow(group.getKey(), label(sub), sub.description())));
                } else {
                    rows.add(new HelpRow(group.getKey(), label(command), command.description()));
                }
            }
        }
        return rows;
    }

    private static String label(CommandDescriptor command) {
        return command.usage().isEmpty() ? command.name() : command.name() + " " + command.usage();
    }

    private record HelpRow(String group, String label, String description) {
    }
}
