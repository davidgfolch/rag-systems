package com.rag.tui.ui;

import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import com.rag.tui.testfixture.TestModules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.rag.tui.testfixture.TestModules.ADVANCED;
import static com.rag.tui.testfixture.TestModules.AGENTIC;
import static com.rag.tui.testfixture.TestModules.BASIC;
import static com.rag.tui.testfixture.TestModules.PROVIDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises startup module resolution against real health endpoints, so a
 * module only counts as running when its socket answers or its child process
 * is alive.
 */
class ModuleBootstrapIntegrationTest {

    private static final String CHOOSE_ACTIVE = "Choose the active module";

    private final ModuleLifecycleManager lifecycle = mock(ModuleLifecycleManager.class);
    private final Prompter prompter = mock(Prompter.class);
    private final ModuleHealthClient health = new ModuleHealthClient(RestClient.builder());

    private StubModuleServer first;
    private StubModuleServer second;
    private String offlineUrl;

    @BeforeEach
    void setUp() throws IOException {
        first = new StubModuleServer();
        second = new StubModuleServer();
        var closing = new StubModuleServer();
        offlineUrl = closing.baseUrl();
        closing.stop();
    }

    @AfterEach
    void tearDown() {
        first.stop();
        second.stop();
    }

    @Test
    void selectsTheOnlyModuleAnsweringOverTheSocket() {
        var registry = registry(ADVANCED,
                TestModules.named(BASIC, first.baseUrl()), TestModules.named(ADVANCED, offlineUrl));
        var result = new ModuleBootstrap(registry, lifecycle, health, prompter).resolve();
        assertThat(result).contains("Active module: " + BASIC, "only running module");
        assertThat(registry.active().name()).isEqualTo(BASIC);
    }

    @Test
    void keepsTheActiveModuleWhenSeveralAnswer() {
        var registry = registry(BASIC,
                TestModules.named(BASIC, first.baseUrl()), TestModules.named(ADVANCED, second.baseUrl()));
        var result = new ModuleBootstrap(registry, lifecycle, health, prompter).resolve();
        assertThat(result).contains("Active module: " + BASIC);
        verify(prompter, never()).pick(anyString(), anyList());
    }

    @Test
    void asksWhichRunningModuleToUseWhenActiveIsDown() {
        var registry = registry(AGENTIC,
                TestModules.named(BASIC, first.baseUrl()), TestModules.named(ADVANCED, second.baseUrl()),
                TestModules.named(AGENTIC, offlineUrl));
        when(prompter.pick(eq(CHOOSE_ACTIVE), anyList())).thenReturn(Optional.of(ADVANCED));
        var result = new ModuleBootstrap(registry, lifecycle, health, prompter).resolve();
        assertThat(result).contains("Active module: " + ADVANCED);
        assertThat(registry.active().name()).isEqualTo(ADVANCED);
    }

    @Test
    void explainsHowToChooseWhenThePromptIsCancelled() {
        var registry = registry(AGENTIC,
                TestModules.named(BASIC, first.baseUrl()), TestModules.named(ADVANCED, second.baseUrl()),
                TestModules.named(AGENTIC, offlineUrl));
        when(prompter.pick(eq(CHOOSE_ACTIVE), anyList())).thenReturn(Optional.empty());
        var result = new ModuleBootstrap(registry, lifecycle, health, prompter).resolve();
        assertThat(result)
                .contains("Several modules running: " + BASIC + ", " + ADVANCED)
                .contains("use 'use <module>'");
        assertThat(registry.active().name()).isEqualTo(AGENTIC);
    }

    @Test
    void countsAChildProcessAsRunningWhenItsSocketIsDown() {
        when(lifecycle.isRunning(ADVANCED)).thenReturn(true);
        var registry = registry(BASIC,
                TestModules.named(BASIC, offlineUrl), TestModules.named(ADVANCED, offlineUrl));
        var result = new ModuleBootstrap(registry, lifecycle, health, prompter).resolve();
        assertThat(result).contains("Active module: " + ADVANCED, "only running module");
        assertThat(registry.active().name()).isEqualTo(ADVANCED);
    }

    @Test
    void explainsHowToRunWhenNothingAnswers() {
        var registry = registry(ADVANCED,
                TestModules.named(BASIC, offlineUrl), TestModules.named(ADVANCED, offlineUrl));
        var result = new ModuleBootstrap(registry, lifecycle, health, prompter).resolve();
        assertThat(result).contains("No module running", ADVANCED, "start <module>", "use <module>");
    }

    @Test
    void ignoresTheProviderCompanionWhenCountingRunningModules() {
        var registry = registry(PROVIDER,
                TestModules.named(BASIC, first.baseUrl()), TestModules.named(PROVIDER, second.baseUrl()));
        var result = new ModuleBootstrap(registry, lifecycle, health, prompter).resolve();
        assertThat(result).contains("Active module: " + BASIC, "only running module");
        assertThat(registry.active().name()).isEqualTo(BASIC);
        verify(prompter, never()).pick(anyString(), anyList());
    }

    private static ModuleRegistry registry(String active, Module... modules) {
        return new ModuleRegistry(List.of(modules), active);
    }
}
