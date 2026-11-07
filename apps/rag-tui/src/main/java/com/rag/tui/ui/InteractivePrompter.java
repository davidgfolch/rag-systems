package com.rag.tui.ui;

import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String CLEAR_EOL = "\u001B[K";
    private static final String HINT = "enter=select esc=cancel up/down/j/k=move type=filter";
    private static final int MAX_IDLE_PICKS = 3;
    private static final int MAX_ROWS = 10;
    /** Title plus hint lines sit above the options, so they are not option rows. */
    private static final int FRAME_CHROME_ROWS = 2;

    private final PickSource pickSource;
    private final PickSource promptSource;
    private final Consumer<String> renderSink;
    private final int rows;
    private final LineEditor editor = new LineEditor();
    /** Lines painted by the last pick frame, used to repaint over them in place. */
    private int frameRows;

    public InteractivePrompter(Terminal terminal, CompletionCandidates completion) {
        this(new TerminalPickSource(terminal, true), new TerminalPickSource(terminal, false), text -> {
            terminal.writer().write(text);
            terminal.flush();
        }, Math.max(1, terminal.getHeight() - 1 - FRAME_CHROME_ROWS), completion);
        // Without raw mode a Unix tty stays canonical and echoey, so arrow keys
        // only reach us after Enter and keys are printed twice (Windows consoles
        // deliver key events either way, which is why this only broke on Linux).
        terminal.enterRawMode();
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
        frameRows = 0;
        render(title, engine);
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

    private static final String CONFIRM_YES = "y";
    private static final String CONFIRM_YES_LONG = "yes";
    private static final String CONFIRM_NO = "n";
    private static final String CONFIRM_NO_LONG = "no";

    @Override
    public boolean confirm(String promptText) {
        String line = prompt(promptText);
        return line != null && line.trim().equalsIgnoreCase(CONFIRM_YES);
    }

    /**
     * Enter/blank overrides this page, Esc ignores it, {@code y}/{@code n}
     * override or ignore the rest of the crawl.
     */
    @Override
    public OverrideDecision overrideDecision(String promptText) {
        editor.start();
        renderPrompt(promptText, editor.view());
        while (true) {
            Key key = promptRead();
            if (key == null) {
                renderSink.accept(CLEAR_LINE);
                return OverrideDecision.SKIP_ALL;
            }
            if (key.type() == KeyType.ESC) {
                renderSink.accept(CLEAR_LINE);
                return OverrideDecision.SKIP;
            }
            if (key.type() == KeyType.ENTER && editor.text().isEmpty()) {
                renderSink.accept(CLEAR_LINE);
                return OverrideDecision.OVERRIDE;
            }
            if (editor.accept(key)) {
                var decision = decisionOf(editor.submitted());
                renderSink.accept(CLEAR_LINE);
                return decision;
            }
            renderPrompt(promptText, editor.view());
        }
    }

    private static OverrideDecision decisionOf(String input) {
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case CONFIRM_YES, CONFIRM_YES_LONG -> OverrideDecision.OVERRIDE_ALL;
            case CONFIRM_NO, CONFIRM_NO_LONG -> OverrideDecision.SKIP_ALL;
            default -> OverrideDecision.SKIP;
        };
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
        } catch (IOException _) {
            return null;
        }
    }

    private void render(String title, PickEngine engine) {
        var lines = new ArrayList<String>();
        lines.add(TerminalStyle.prompt(title) + " [filter: " + engine.filter() + "]");
        lines.add(TerminalStyle.info(HINT));
        var list = engine.visible();
        int start = Math.max(0, engine.cursor() - rows + 1);
        int end = Math.min(list.size(), start + rows);
        for (int i = start; i < end; i++) {
            var c = list.get(i);
            String prefix = (i == engine.cursor()) ? "> " : "  ";
            String line = prefix + c.label() + (c.detail().isEmpty() ? "" : " - " + c.detail());
            lines.add(i == engine.cursor() ? TerminalStyle.command(line) : line);
        }
        if (list.isEmpty()) {
            lines.add(TerminalStyle.error("  no matches for '" + engine.filter() + "' - backspace to widen"));
        }
        var sb = new StringBuilder("\r");
        if (frameRows > 0) sb.append(CSI).append(frameRows).append('A');
        sb.append(CSI).append('J');
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\r\n");
            sb.append(lines.get(i)).append(CLEAR_EOL);
        }
        sb.append("\r\n");
        frameRows = lines.size();
        renderSink.accept(sb.toString());
    }

    interface PickSource {
        Key read() throws IOException;
    }
}
