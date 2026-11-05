package com.rag.tui.help;

import java.util.LinkedHashMap;
import java.util.Map;

/** Detailed help text for session and module lifecycle commands. */
public final class SessionHelpText {

    private SessionHelpText() {
    }

    public static Map<String, String> details() {
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
        details.put("quit", """
                Usage:
                  quit      exit the terminal (alias: exit)
                """);
        return details;
    }
}
