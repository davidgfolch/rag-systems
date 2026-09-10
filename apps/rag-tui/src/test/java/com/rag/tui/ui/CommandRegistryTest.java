package com.rag.tui.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest(name = "findByName(\"{0}\") is present")
    @ValueSource(strings = {"HELP", "help", "Help"})
    void findsCommandByNameCaseInsensitive(String name) {
        assertThat(sut.findByName(name)).isPresent();
    }

    @Test
    void returnsEmptyForUnknownCommand() {
        assertThat(sut.findByName("frobnicate")).isEmpty();
    }

    @ParameterizedTest(name = "filter(\"{0}\") → size {1}")
    @CsvSource({
            "a,4",
            "ADD,3"
    })
    void filterReturnsExpectedCount(String prefix, int expectedSize) {
        assertThat(sut.filter(prefix)).hasSize(expectedSize);
    }

    @Test
    void filtersCommandsByPrefix() {
        assertThat(sut.filter("a").stream().map(CommandDescriptor::name))
                .contains("add-file", "add-folder", "add-url", "ask");
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
                .contains("connect catalog [<provider>]", "connect chat <provider> <model>",
                        "connect embedding <provider> <model>", "connect refresh")
                .doesNotContain("/ to browse commands");
    }

    @Test
    void tabulatesDescriptionColumn() {
        var descStarts = sut.generateUsage().lines()
                .filter(line -> line.startsWith("  "))
                .map(line -> line.lastIndexOf("  ") + 2)
                .distinct()
                .toList();
        assertThat(descStarts).hasSize(1);
    }
}