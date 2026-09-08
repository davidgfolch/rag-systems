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
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.rag.tui.ui.Key.KeyType;

/**
 * JLine-backed {@link Prompter}: a filterable single-select list plus a readline
 * free-text prompt, over a real terminal. The selection loop runs against an
 * injectable {@link PickSource} (pure selection logic stays in {@link PickEngine}),
 * so it is fully unit-testable without a live terminal.
 */
public class InteractivePrompter implements Prompter {

    private final LineReader reader;
    private final PickSource pickSource;
    private final Consumer<String> renderSink;
    private final int rows;

    public InteractivePrompter(Terminal terminal, Supplier<Collection<String>> commandCandidates) {
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(new ArgumentCompleter(new StringsCompleter(commandCandidates)))
                .build();
        this.pickSource = new TerminalPickSource(terminal);
        this.renderSink = text -> {
            terminal.writer().write(text);
            terminal.flush();
        };
        this.rows = Math.max(1, terminal.getHeight() - 1);
    }

    InteractivePrompter(PickSource pickSource, Consumer<String> renderSink, int rows) {
        this.reader = null;
        this.pickSource = pickSource;
        this.renderSink = renderSink;
        this.rows = Math.min(rows, 10);
    }

    @Override
    public Optional<String> pick(String title, List<Choice> choices) {
        if (choices.isEmpty()) return Optional.empty();
        var engine = new PickEngine(choices);
        int idle = 0;
        while (true) {
            Key key = read();
            if (key == null) return Optional.empty();
            if (key.type() == KeyType.NONE) {
                if (++idle > 3) return Optional.empty();
                continue;
            }
            idle = 0;
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
                case TYPE -> engine.append(key.value());
                default -> { }
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

    private Key read() {
        try {
            return pickSource.read();
        } catch (IOException e) {
            return null;
        }
    }

    private void render(String title, PickEngine engine) {
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
        renderSink.accept(sb.toString());
    }

    interface PickSource {
        Key read() throws IOException;
    }

    /** Decodes raw terminal bytes (including ESC sequences) into {@link Key}s. */
    static final class TerminalPickSource implements PickSource {
        private final Terminal terminal;

        TerminalPickSource(Terminal terminal) {
            this.terminal = terminal;
        }

        @Override
        public Key read() throws IOException {
            int first = terminal.reader().read();
            if (first < 0) return null;
            if (first == 3 || first == 4) return new Key(KeyType.ESC, ' ');
            if (first == 27) {
                int second = terminal.reader().read();
                if (second != '[') return new Key(KeyType.ESC, ' ');
                int third = terminal.reader().read();
                return switch (third) {
                    case 'A' -> new Key(KeyType.UP, ' ');
                    case 'B' -> new Key(KeyType.DOWN, ' ');
                    default -> new Key(KeyType.NONE, ' ');
                };
            }
            if (first == 13 || first == 10) return new Key(KeyType.ENTER, ' ');
            if (first == 127 || first == 8) return new Key(KeyType.BACKSPACE, ' ');
            if (first == 'k') return new Key(KeyType.UP, ' ');
            if (first == 'j') return new Key(KeyType.DOWN, ' ');
            if (first == 'q') return new Key(KeyType.ESC, ' ');
            if (Character.isLetterOrDigit(first) || first == '-' || first == '_'
                    || first == '.' || first == ' ') {
                return new Key(KeyType.TYPE, (char) first);
            }
            return new Key(KeyType.NONE, ' ');
        }
    }
}
