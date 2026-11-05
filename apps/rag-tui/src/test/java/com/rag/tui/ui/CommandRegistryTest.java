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
        assertThat(sut.all()).hasSize(16);
        assertThat(sut.all().stream().map(CommandDescriptor::name))
                .contains("help", "modules", "use", "start", "stop",
                        "documents", "delete", "add-file", "add-folder", "add-url", "add-url-cancel",
                        "ask", "history", "connect", "quit", "variant");
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
            "a,5",
            "ADD,4"
    })
    void filterReturnsExpectedCount(String prefix, int expectedSize) {
        assertThat(sut.filter(prefix)).hasSize(expectedSize);
    }

    @Test
    void filtersCommandsByPrefix() {
        assertThat(sut.filter("a").stream().map(CommandDescriptor::name))
                .contains("add-file", "add-folder", "add-url", "add-url-cancel", "ask");
    }

    @Test
    void returnsAllCommandsForEmptyPrefix() {
        assertThat(sut.filter("")).hasSize(16);
    }

    @Test
    void generatesUsageWithAllCommands() {
        String usage = sut.generateUsage();
        assertThat(usage)
                .contains("Available commands:")
                .contains("help", "modules", "use", "start", "stop")
                .contains("documents", "delete", "add-file", "add-folder", "add-url", "add-url-cancel",
                        "ask", "history", "connect", "quit")
                .contains("connect catalog [<provider>]", "connect chat <provider> <model>",
                        "connect embedding <provider> <model>", "connect refresh")
                .doesNotContain("/ to browse commands");
    }

    @Test
    void detailedHelpDocumentsVariantArgumentsAndRecommendedValues() {
        assertThat(sut.generateCommandUsage("variant"))
                .contains("variant - list, create or activate pipeline variants")
                .contains("variant add <strategy> [<size>] [<overlap>]")
                .contains("Recommended values:")
                .contains("RECURSIVE 512/128", "256/64")
                .contains("0 <= overlap < size");
    }

    @Test
    void detailedHelpIsAvailableForEveryCommand() {
        for (CommandDescriptor command : sut.all()) {
            assertThat(sut.generateCommandUsage(command.name()))
                    .as(command.name())
                    .contains("Usage:");
        }
    }

    @Test
    void detailedHelpResolvesConnectSubcommandsToConnect() {
        assertThat(sut.generateCommandUsage("connect chat"))
                .contains("connect - browse models or switch chat/embedding")
                .contains("connect catalog [<provider>]");
    }

    @Test
    void detailedHelpIsCaseAndWhitespaceInsensitive() {
        assertThat(sut.generateCommandUsage("  VARIANT  "))
                .contains("variant - list, create or activate pipeline variants");
    }

    @Test
    void blankArgumentStillShowsTheCommandOverview() {
        assertThat(sut.generateCommandUsage("  ")).contains("Available commands:");
    }

    @Test
    void unknownCommandGetsAnActionableMessage() {
        assertThat(sut.generateCommandUsage("frobnicate"))
                .contains("No help for 'frobnicate'", "Type 'help'");
    }

    @Test
    void groupsHelpByConcept() {
        var usage = sut.generateUsage();
        assertThat(usage).contains("Modules", "Documents", "Chat", "Providers", "Pipeline variants", "Session");
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