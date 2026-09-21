package com.rag.tui.ui;

import org.jline.terminal.Terminal;

import java.io.IOException;

import static com.rag.tui.ui.Key.KeyType;

/** Decodes raw terminal bytes (including ESC sequences) into {@link Key}s. */
final class TerminalPickSource implements InteractivePrompter.PickSource {

    private static final int ESC = 27;
    private static final int CSI = '[';
    private static final int SS3 = 'O';
    private static final int TIMEOUT_MS = 50;
    private static final char SPACE = ' ';

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
        if (first == ESC) return escapeSequence();
        Key key = decode(first);
        return key != null ? key : key(KeyType.NONE);
    }

    private Key escapeSequence() throws IOException {
        int second = terminal.reader().read(TIMEOUT_MS);
        if (second == CSI) return csiSequence();
        if (second == SS3) return ss3Sequence();
        return key(KeyType.ESC);
    }

    /** Parses a CSI sequence: parameter bytes then a single final byte. */
    private Key csiSequence() throws IOException {
        var params = new StringBuilder();
        int ch;
        while ((ch = terminal.reader().read()) >= 0) {
            if (ch >= '0' && ch <= '?') {
                params.append((char) ch);
            } else if (ch < ' ' || ch > '/') {
                return mapCsi(params.toString(), ch);
            }
        }
        return key(KeyType.NONE);
    }

    private Key mapCsi(String params, int fin) {
        return switch (fin) {
            case 'A' -> key(KeyType.UP);
            case 'B' -> key(KeyType.DOWN);
            case 'C' -> key(hasCtrl(params) ? KeyType.WORD_RIGHT : KeyType.RIGHT);
            case 'D' -> key(hasCtrl(params) ? KeyType.WORD_LEFT : KeyType.LEFT);
            case 'H' -> key(KeyType.HOME);
            case 'F' -> key(KeyType.END);
            case '~' -> tilde(params);
            case 'u' -> csiU(params);
            default -> key(KeyType.NONE);
        };
    }

    private Key ss3Sequence() throws IOException {
        return switch (terminal.reader().read()) {
            case 'A' -> key(KeyType.UP);
            case 'B' -> key(KeyType.DOWN);
            case 'C' -> key(KeyType.RIGHT);
            case 'D' -> key(KeyType.LEFT);
            case 'H' -> key(KeyType.HOME);
            case 'F' -> key(KeyType.END);
            default -> key(KeyType.NONE);
        };
    }

    private Key tilde(String params) {
        return switch (params) {
            case "1", "7" -> key(KeyType.HOME);
            case "4", "8" -> key(KeyType.END);
            case "3" -> key(KeyType.DELETE);
            case "3;5", "127;5", "8;5" -> key(KeyType.WORD_BACKSPACE);
            default -> key(KeyType.NONE);
        };
    }

    private Key csiU(String params) {
        return switch (params) {
            case "127;5", "8;5" -> key(KeyType.WORD_BACKSPACE);
            default -> key(KeyType.NONE);
        };
    }

    private static boolean hasCtrl(String params) {
        return params.equals("1;5") || params.endsWith(";5");
    }

    private Key decode(int first) {
        if (first == 3 || first == 4) return key(KeyType.ESC);
        if (first == 13 || first == 10) return key(KeyType.ENTER);
        if (first == 127) return key(KeyType.BACKSPACE);
        if (first == 8 || first == 31 || first == 23) return key(KeyType.WORD_BACKSPACE);
        if (navigationKeys) {
            if (first == 'k') return key(KeyType.UP);
            if (first == 'j') return key(KeyType.DOWN);
            if (first == 'q') return key(KeyType.ESC);
        }
        if (isPrintable(first)) return new Key(KeyType.TYPE, (char) first);
        return null;
    }

    /**
     * Any non-control character is typable so pasted file paths and URLs
     * survive intact (':', '\\', '/', '#', '~', '!'). Only C0 control bytes
     * and DEL are reserved for command decoding above.
     */
    private static boolean isPrintable(int code) {
        return code >= 32 && code != 127;
    }

    private static Key key(KeyType type) {
        return new Key(type, SPACE);
    }
}
