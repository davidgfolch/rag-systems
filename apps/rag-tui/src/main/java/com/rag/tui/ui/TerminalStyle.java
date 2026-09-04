package com.rag.tui.ui;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

public final class TerminalStyle {

    private TerminalStyle() {}

    public static String welcome(String text) {
        return styled(text, AttributedStyle.BOLD.foreground(AttributedStyle.CYAN));
    }

    public static String error(String text) {
        return styled(text, AttributedStyle.BOLD.foreground(AttributedStyle.RED));
    }

    public static String success(String text) {
        return styled(text, AttributedStyle.BOLD.foreground(AttributedStyle.GREEN));
    }

    public static String info(String text) {
        return styled(text, AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN));
    }

    public static String command(String text) {
        return styled(text, AttributedStyle.BOLD.foreground(AttributedStyle.YELLOW));
    }

    public static String prompt(String text) {
        return styled(text, AttributedStyle.BOLD);
    }

    private static String styled(String text, AttributedStyle style) {
        return new AttributedString(text, style).toAnsi();
    }
}
