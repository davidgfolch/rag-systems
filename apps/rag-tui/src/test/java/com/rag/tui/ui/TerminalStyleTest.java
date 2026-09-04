package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalStyleTest {

    @Test
    void welcomeWrapsTextInBoldCyan() {
        String result = TerminalStyle.welcome("hello");
        assertThat(result).contains("hello");
        assertThat(result).contains("\033[");
    }

    @Test
    void errorWrapsTextInBoldRed() {
        String result = TerminalStyle.error("fail");
        assertThat(result).contains("fail");
        assertThat(result).contains("\033[");
    }

    @Test
    void successWrapsTextInBoldGreen() {
        String result = TerminalStyle.success("ok");
        assertThat(result).contains("ok");
        assertThat(result).contains("\033[");
    }

    @Test
    void infoWrapsTextInCyan() {
        String result = TerminalStyle.info("detail");
        assertThat(result).contains("detail");
        assertThat(result).contains("\033[");
    }

    @Test
    void commandWrapsTextInBoldYellow() {
        String result = TerminalStyle.command("/help");
        assertThat(result).contains("/help");
        assertThat(result).contains("\033[");
    }

    @Test
    void promptWrapsTextInBold() {
        String result = TerminalStyle.prompt("> ");
        assertThat(result).contains("> ");
        assertThat(result).contains("\033[");
    }
}
