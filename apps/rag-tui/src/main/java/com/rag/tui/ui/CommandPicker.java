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
        var filter = new StringBuilder(initialFilter);
        List<CommandDescriptor> filtered = registry.filter(filter.toString());
        int selected = 0;
        int displayedLines = filtered.size() + 1;
        NonBlockingReader reader = terminal.reader();

        try {
            showList(filtered, selected);
            while (true) {
                KeyResult result = handleKey(reader, filter, filtered, selected);
                if (result.action() == Action.EXIT) return null;
                if (result.action() == Action.SELECT) return result.command();
                selected = result.selected();

                clearLines(displayedLines);
                filtered = registry.filter(filter.toString());
                selected = Math.clamp(selected, 0, Math.max(0, filtered.size() - 1));
                showList(filtered, selected);
                displayedLines = filtered.size() + 1;
            }
        } catch (IOException e) {
            return null;
        }
    }

    private KeyResult handleKey(NonBlockingReader reader, StringBuilder filter,
                                 List<CommandDescriptor> filtered, int selected) throws IOException {
        int ch = reader.read(50);
        if (ch == -1) return exit();
        if (ch == '\033') return handleEscapeKey(reader, filtered.size(), selected);
        if (ch == '\n' || ch == '\r') return select(selected, filtered);
        if (ch == 127 || ch == 8) return backspace(filter, selected);
        if (ch >= 32) return type(filter, ch, selected);
        return exit();
    }

    private KeyResult handleEscapeKey(NonBlockingReader reader, int size, int selected) throws IOException {
        if (size == 0) return KeyResult.continueSelection(selected);
        int next = reader.read(50);
        if (next == -1) return exit();
        if (next == '[') {
            int code = reader.read(50);
            if (code == 'A') return KeyResult.continueSelection((int) Math.clamp((long) selected - 1, 0, (long) size - 1));
            if (code == 'B') return KeyResult.continueSelection((int) Math.clamp((long) selected + 1, 0, (long) size - 1));
            return KeyResult.continueSelection(selected);
        }
        return exit();
    }

    private KeyResult select(int selected, List<CommandDescriptor> filtered) {
        return selected < filtered.size()
                ? new KeyResult(Action.SELECT, filtered.get(selected), selected)
                : exit();
    }

    private KeyResult backspace(StringBuilder filter, int selected) {
        if (filter.length() > 0) filter.setLength(filter.length() - 1);
        return KeyResult.continueSelection(selected);
    }

    private KeyResult type(StringBuilder filter, int ch, int selected) {
        filter.append((char) ch);
        return KeyResult.continueSelection(selected);
    }

    private static KeyResult exit() {
        return new KeyResult(Action.EXIT, null, 0);
    }

    private void showList(List<CommandDescriptor> commands, int selected) {
        StringBuilder sb = new StringBuilder();
        sb.append(TerminalStyle.command("Commands:\n"));
        for (int i = 0; i < commands.size(); i++) {
            CommandDescriptor cmd = commands.get(i);
            String marker = (i == selected) ? "> " : "  ";
            sb.append(marker).append(cmd.name()).append("  ")
              .append(TerminalStyle.info(cmd.description())).append("\n");
        }
        if (commands.isEmpty()) {
            sb.append("  (no matching commands)\n");
        }
        sb.append("\nPress Enter to select, Esc to cancel\n");
        terminal.writer().write(sb.toString());
        terminal.writer().flush();
    }

    private void clearLines(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(CLEAR_LINE);
            if (i < count - 1) sb.append(MOVE_UP.formatted(1));
        }
        terminal.writer().write(sb.toString());
        terminal.writer().flush();
    }

    private enum Action {
        NONE, EXIT, SELECT
    }

    private record KeyResult(Action action, CommandDescriptor command, int selected) {
        static KeyResult continueSelection(int selected) {
            return new KeyResult(Action.NONE, null, selected);
        }
    }
}
