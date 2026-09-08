package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRegistryTest {

    private final CommandRegistry sut = new CommandRegistry();

    @Test
    void containsAllExpectedCommands() {
        assertThat(sut.all()).hasSize(14);
        assertThat(sut.all().stream().map(CommandDescriptor::name))
                .contains("help", "modules", "use", "start", "stop",
                        "documents", "delete", "add-file", "add-folder", "add-url", "ask", "history",
                        "connect", "quit");
    }

    @Test
    void findsCommandByNameCaseInsensitive() {
        assertThat(sut.findByName("HELP")).isPresent();
        assertThat(sut.findByName("help")).isPresent();
        assertThat(sut.findByName("Help")).isPresent();
    }

    @Test
    void returnsEmptyForUnknownCommand() {
        assertThat(sut.findByName("frobnicate")).isEmpty();
    }

    @Test
    void filtersCommandsByPrefix() {
        assertThat(sut.filter("a")).hasSize(4);
        assertThat(sut.filter("a").stream().map(CommandDescriptor::name))
                .contains("add-file", "add-folder", "add-url", "ask");
    }

    @Test
    void filtersCommandsCaseInsensitive() {
        assertThat(sut.filter("ADD")).hasSize(3);
    }

    @Test
    void returnsAllCommandsForEmptyPrefix() {
        assertThat(sut.filter("")).hasSize(14);
    }

    @Test
    void generatesUsageWithAllCommands() {
        String usage = sut.generateUsage();

        assertThat(usage)
                .contains("Available commands:")
                .contains("help", "modules", "use", "start", "stop")
                .contains("documents", "delete", "add-file", "add-folder", "add-url", "ask", "history", "connect", "quit")
                .doesNotContain("/ to browse commands");
    }
}
