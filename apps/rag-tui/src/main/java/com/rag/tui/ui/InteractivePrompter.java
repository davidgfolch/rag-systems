package com.rag.tui.ui;

import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.rag.tui.ui.Key.KeyType;

/**
 * JLine-backed {@link Prompter}: a filterable single-select list plus an
 * auto-completing readline prompt, over a real terminal. The prompt loop runs
 * against an injectable {@link PickSource} (pure selection logic stays in
 * {@link PickEngine} and {@link LineEditor}), so it is fully unit-testable
 * without a live terminal.
 */
public class InteractivePrompter implements Prompter {

    private static final String CSI = "\u001B[";
    private static final String CLEAR_LINE = "\u001B[J\r\n";
    private static final int MAX_IDLE_PICKS = 3;
    private static final int MAX_ROWS = 10;

    private final PickSource pickSource;
    private final PickSource promptSource;
    private final Consumer<String> renderSink;
    private final int rows;
    private final LineEditor editor = new LineEditor();

    public InteractivePrompter(Terminal terminal, CompletionCandidates completion) {
        this(new TerminalPickSource(terminal, true), new TerminalPickSource(terminal, false), text -> {
            terminal.writer().write(text);
            terminal.flush();
        }, Math.max(1, terminal.getHeight() - 1), completion);
    }

    InteractivePrompter(PickSource pickSource, Consumer<String> renderSink, int rows) {
        this(pickSource, pickSource, renderSink, rows, (line, cursor) -> List.of());
    }

    InteractivePrompter(PickSource pickSource, Consumer<String> renderSink, int rows,
                        CompletionCandidates completion) {
        this(pickSource, pickSource, renderSink, rows, completion);
    }

    private InteractivePrompter(PickSource pickSource, PickSource promptSource,
                                Consumer<String> renderSink, int rows, CompletionCandidates completion) {
        this.pickSource = pickSource;
        this.promptSource = promptSource;
        this.renderSink = renderSink;
        this.rows = Math.min(rows, MAX_ROWS);
        this.editor.setCompletion(completion);
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
                if (++idle > MAX_IDLE_PICKS) return Optional.empty();
                continue;
            }
            idle = 0;
            switch (key.type()) {
                case ESC -> {
                    return Optional.empty();
                }
                case ENTER -> {
                    var list = engine.visible();
                    if (!list.isEmpty()) {
                        return Optional.of(list.get(engine.cursor()).value());
                    }
                }
                case UP -> engine.prev();
                case DOWN -> engine.next();
                case BACKSPACE -> engine.backspace();
                case TYPE -> engine.append(key.value());
                default -> { /* LEFT/RIGHT are not used in pick mode */ }
            }
            render(title, engine);
        }
    }

    @Override
    public String prompt(String promptText) {
        editor.start();
        renderPrompt(promptText, editor.view());
        Key key;
        do {
            key = promptRead();
            if (key == null) return editor.text().isEmpty() ? null : editor.text();
        } while (!step(promptText, key));
        renderSink.accept(CLEAR_LINE);
        return editor.submitted();
    }

    private boolean step(String promptText, Key key) {
        if (key.type() == KeyType.NONE) return false;
        if (key.type() == KeyType.ENTER && editor.popupVisible() && editor.selectPopup()) {
            renderPrompt(promptText, editor.view());
            return false;
        }
        if (editor.accept(key)) return true;
        renderPrompt(promptText, editor.view());
        return false;
    }

    private void renderPrompt(String promptText, LineEditor.View view) {
        var sb = new StringBuilder("\r\u001B[2K\u001B[J");
        sb.append(TerminalStyle.prompt(promptText)).append(view.line());
        sb.append(CSI).append(view.cursor() + promptText.length() + 1).append('G');
        int popupRows = 0;
        if (view.popupVisible()) {
            popupRows = renderPopup(sb, view);
            sb.append(CSI).append(popupRows).append('A');
            sb.append(CSI).append(view.cursor() + promptText.length() + 1).append('G');
        }
        renderSink.accept(sb.toString());
    }

    private int renderPopup(StringBuilder sb, LineEditor.View view) {
        int popupRows = Math.min(view.popup().size(), rows);
        int start = Math.max(0, view.popupCursor() - popupRows + 1);
        int end = Math.min(view.popup().size(), start + popupRows);
        for (int i = start; i < end; i++) {
            String prefix = (i == view.popupCursor()) ? "> " : "  ";
            String line = prefix + view.popup().get(i);
            sb.append("\n").append(i == view.popupCursor() ? TerminalStyle.command(line) : line);
        }
        return popupRows;
    }

    private Key read() {
        try {
            return pickSource.read();
        } catch (IOException _) {
            return null;
        }
    }

    private Key promptRead() {
        try {
            return promptSource.read();
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
        private final boolean navigationKeys;

        TerminalPickSource(Terminal terminal) {
            this(terminal, true);
        }

        TerminalPickSource(Terminal terminal, boolean navigationKeys) {
            this.terminal = terminal;
            this.navigationKeys = navigationKeys;
        }

        @Override
        public Key read() throws IOException {
            int first = terminal.reader().read();
            if (first < 0) return null;
            if (first == 27) return escapeSequence();
            Key key = decode(first);
            return key != null ? key : new Key(KeyType.NONE, ' ');
        }

        private Key escapeSequence() throws IOException {
            int second = terminal.reader().read(50);
            if (second != '[' && second != 'O') return new Key(KeyType.ESC, ' ');
            int third = terminal.reader().read();
            return switch (third) {
                case 'A' -> new Key(KeyType.UP, ' ');
                case 'B' -> new Key(KeyType.DOWN, ' ');
                case 'C' -> new Key(KeyType.RIGHT, ' ');
                case 'D' -> new Key(KeyType.LEFT, ' ');
                default -> new Key(KeyType.NONE, ' ');
            };
        }

        private Key decode(int first) {
            if (first == 3 || first == 4) return new Key(KeyType.ESC, ' ');
            if (first == 13 || first == 10) return new Key(KeyType.ENTER, ' ');
            if (first == 127 || first == 8) return new Key(KeyType.BACKSPACE, ' ');
            if (navigationKeys) {
                if (first == 'k') return new Key(KeyType.UP, ' ');
                if (first == 'j') return new Key(KeyType.DOWN, ' ');
                if (first == 'q') return new Key(KeyType.ESC, ' ');
            }
            if (isPrintable(first)) return new Key(KeyType.TYPE, (char) first);
            return null;
        }

        private static boolean isPrintable(int code) {
            return Character.isLetterOrDigit(code) || code == '-' || code == '_'
                    || code == '.' || code == ' ';
        }
    }
}