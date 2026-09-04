package com.rag.tui.ui;

import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.List;

public class CommandPicker {

    private static final String CLEAR_LINE = "\033[2K\r";
    private static final String MOVE_UP = "\033[%dA";

    private final CommandRegistry registry;
    private final Terminal terminal;

    public CommandPicker(CommandRegistry registry, Terminal terminal) {
        this.registry = registry;
        this.terminal = terminal;
    }

    public CommandDescriptor pick(String initialFilter) {
        List<CommandDescriptor> filtered = registry.filter(initialFilter);
        int selected = 0;
        int displayedLines = 0;

        NonBlockingReader reader = terminal.reader();
        try {
            showList(filtered, selected);
            displayedLines = filtered.size() + 1;

            while (true) {
                int ch = reader.read(50);
                if (ch == -1) return null;

                if (ch == '\033') {
                    int next = reader.read(50);
                    if (next == -1) return null;
                    if (next == '[') {
                        int code = reader.read(50);
                        if (code == 'A') {
                            selected = Math.max(0, selected - 1);
                        } else if (code == 'B') {
                            selected = Math.min(filtered.size() - 1, selected + 1);
                        }
                    } else {
                        return null;
                    }
                } else if (ch == '\n' || ch == '\r') {
                    return selected < filtered.size() ? filtered.get(selected) : null;
                } else if (ch == 27) {
                    return null;
                } else if (ch == 127 || ch == 8) {
                    if (!initialFilter.isEmpty()) {
                        initialFilter = initialFilter.substring(0, initialFilter.length() - 1);
                    }
                } else if (ch >= 32) {
                    initialFilter += (char) ch;
                } else {
                    return null;
                }

                clearLines(displayedLines);
                filtered = registry.filter(initialFilter);
                selected = Math.min(selected, Math.max(0, filtered.size() - 1));
                showList(filtered, selected);
                displayedLines = filtered.size() + 1;
            }
        } catch (IOException e) {
            return null;
        }
    }

    private void showList(List<CommandDescriptor> commands, int selected) throws IOException {
        terminal.writer().write(TerminalStyle.command("Commands:\n"));
        for (int i = 0; i < commands.size(); i++) {
            CommandDescriptor cmd = commands.get(i);
            String marker = (i == selected) ? "> " : "  ";
            String line = marker + cmd.name() + "  " + TerminalStyle.info(cmd.description()) + "\n";
            terminal.writer().write(line);
        }
        if (commands.isEmpty()) {
            terminal.writer().write("  (no matching commands)\n");
        }
        terminal.writer().write("\nPress Enter to select, Esc to cancel\n");
        terminal.writer().flush();
    }

    private void clearLines(int count) throws IOException {
        for (int i = 0; i < count; i++) {
            terminal.writer().write(CLEAR_LINE);
            if (i < count - 1) {
                terminal.writer().write(MOVE_UP.formatted(1));
            }
        }
        terminal.writer().flush();
    }
}
