package com.rag.tui.ui;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * JLine-backed {@link Prompter}: a filterable single-select list plus a readline
 * free-text prompt, over a real terminal. Pure selection logic lives in
 * {@link PickEngine} so it is unit-testable without a terminal.
 */
public class InteractivePrompter implements Prompter {

    private final Terminal terminal;
    private final LineReader reader;

    public InteractivePrompter(Terminal terminal, Supplier<Collection<String>> commandCandidates) {
        this.terminal = terminal;
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new ArgumentCompleter(new StringsCompleter(commandCandidates)))
                .build();
    }

    @Override
    public Optional<String> pick(String title, List<Choice> choices) {
        if (choices.isEmpty()) return Optional.empty();
        var engine = new PickEngine(choices);
        while (true) {
            var key = readKey();
            if (key == null) return Optional.empty();
            switch (key.type()) {
                case ESC -> {
                    return Optional.empty();
                }
                case ENTER -> {
                    var list = engine.visible();
                    if (list.isEmpty()) continue;
                    return Optional.of(list.get(engine.cursor()).value());
                }
                case UP -> engine.prev();
                case DOWN -> engine.next();
                case BACKSPACE -> engine.backspace();
                case TYPE -> engine.append(key.charValue());
                case NONE -> { }
            }
            render(title, engine);
        }
    }

    @Override
    public String prompt(String promptText) {
        try {
            String line = reader.readLine(TerminalStyle.prompt(promptText));
            return line == null ? null : line.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private void render(String title, PickEngine engine) {
        var rows = Math.min(terminal.getHeight() - 1, 10);
        var sb = new StringBuilder();
        sb.append("\r\u001B[2K").append(TerminalStyle.prompt(title))
                .append(" [filter: ").append(engine.filter()).append("]\n")
                .append(TerminalStyle.info("enter=select esc=cancel up/down/j/k=move type=filter"));
        var list = engine.visible();
        int start = Math.max(0, engine.cursor() - rows + 1);
        int end = Math.min(list.size(), start + rows);
        for (int i = start; i < end; i++) {
            var c = list.get(i);
            String prefix = (i == engine.cursor()) ? "> " : "  ";
            String line = prefix + c.label() + (c.detail().isEmpty() ? "" : " - " + c.detail());
            sb.append("\n").append(i == engine.cursor() ? TerminalStyle.command(line) : line);
        }
        sb.append("\n");
        terminal.writer().write(sb.toString());
        terminal.flush();
    }

    private EmittedKey readKey() {
        int first;
        try {
            first = terminal.reader().read();
        } catch (IOException e) {
            return new EmittedKey(KeyType.NONE, ' ');
        }
        if (first < 0) return null;
        if (first == 3 || first == 4) return new EmittedKey(KeyType.ESC, ' ');
        if (first == 27) {
            try {
                int second = terminal.reader().read();
                if (second != '[') return new EmittedKey(KeyType.ESC, ' ');
                int third = terminal.reader().read();
                return switch (third) {
                    case 'A' -> new EmittedKey(KeyType.UP, ' ');
                    case 'B' -> new EmittedKey(KeyType.DOWN, ' ');
                    default -> new EmittedKey(KeyType.NONE, ' ');
                };
            } catch (IOException e) {
                return new EmittedKey(KeyType.ESC, ' ');
            }
        }
        if (first == 13 || first == 10) return new EmittedKey(KeyType.ENTER, ' ');
        if (first == 127 || first == 8) return new EmittedKey(KeyType.BACKSPACE, ' ');
        if (first == 'k') return new EmittedKey(KeyType.UP, ' ');
        if (first == 'j') return new EmittedKey(KeyType.DOWN, ' ');
        if (first == 'q') return new EmittedKey(KeyType.ESC, ' ');
        if (Character.isLetterOrDigit(first) || first == '-' || first == '_' || first == '.' || first == ' ') {
            return new EmittedKey(KeyType.TYPE, (char) first);
        }
        return new EmittedKey(KeyType.NONE, ' ');
    }

    private enum KeyType { ESC, ENTER, UP, DOWN, BACKSPACE, TYPE, NONE }

    private record EmittedKey(KeyType type, char charValue) {}
}
