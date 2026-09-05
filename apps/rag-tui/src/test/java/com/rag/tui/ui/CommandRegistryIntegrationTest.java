package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRegistryIntegrationTest {

    private final CommandRegistry sut = new CommandRegistry();

    @Test
    void usageTextListsEveryRegisteredCommand() {
        String usage = sut.generateUsage();

        for (CommandDescriptor cmd : sut.all()) {
            assertThat(usage).contains(cmd.name());
        }
    }

    @Test
    void filterReturnsOnlyMatchingCommands() {
        assertThat(sut.filter("mod")).hasSize(1);
        assertThat(sut.filter("mod").getFirst().name()).isEqualTo("modules");
        assertThat(sut.filter("zzz")).isEmpty();
    }
}
