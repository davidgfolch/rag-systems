package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.rag.tui.testfixture.TestDocumentSummaries.withId;
import static com.rag.tui.testfixture.TestModules.ADVANCED;
import static com.rag.tui.testfixture.TestModules.ADVANCED_URL;
import static com.rag.tui.testfixture.TestModules.BASIC;
import static com.rag.tui.testfixture.TestModules.BASIC_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherTest extends AbstractCommandDispatcherTest {

    @Test
    void listsModulesWithActiveAndState() {
        when(lifecycle.isRunning(BASIC)).thenReturn(true);
        var result = handle("modules");
        assertThat(result)
                .contains(BASIC, "running", "(active)")
                .contains(ADVANCED, "stopped");
    }

    @Test
    void marksExternallyStartedModuleAsRunning() {
        when(lifecycle.isRunning(ADVANCED)).thenReturn(false);
        when(healthClient.isUp(ADVANCED_URL)).thenReturn(true);
        var result = handle("modules");
        assertThat(result).contains(ADVANCED, "running (external)");
    }

    @Test
    void listsDocumentsFromReachableModules() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(healthClient.isUp(ADVANCED_URL)).thenReturn(false);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withId("d1")));
        var result = handle("documents");
        assertThat(result)
                .contains(BASIC)
                .contains("note.txt", "3 chunks", "[d1]")
                .doesNotContain(ADVANCED);
    }

    @Test
    void reportsNoReachableModulesForDocuments() {
        when(healthClient.isUp(anyString())).thenReturn(false);
        var result = handle("documents");
        assertThat(result).contains("No rag-* modules are reachable");
    }

    @Test
    void switchesActiveModule() {
        var result = handle("use " + ADVANCED);
        assertThat(result).contains("Active module: " + ADVANCED);
        assertThat(registry.active().name()).isEqualTo(ADVANCED);
    }

    @Test
    void rejectsUnknownModule() {
        var result = handle("use nope");
        assertThat(result).contains("Unknown module");
    }

    @Test
    void usePromptsForModuleWhenNoArgumentGiven() {
        when(prompter.pick(eq("Switch active module"), anyList())).thenReturn(Optional.of(ADVANCED));
        var result = handle("use");
        assertThat(result).contains("Active module: " + ADVANCED);
        assertThat(registry.active().name()).isEqualTo(ADVANCED);
    }

    @Test
    void useCancelledPromptIsNoOp() {
        when(prompter.pick(eq("Switch active module"), anyList())).thenReturn(Optional.empty());
        var result = handle("use");
        assertThat(result).isEmpty();
        assertThat(registry.active().name()).isEqualTo(BASIC);
    }

    @Test
    void startPromptsForModuleWhenNoArgumentGiven() {
        when(prompter.pick(eq("Start module"), anyList())).thenReturn(Optional.of(BASIC));
        when(lifecycle.start(registry.find(BASIC).get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(true);
        var result = handle("start");
        assertThat(result).contains("Started " + BASIC, "ready");
    }

    @Test
    void startsModuleWaitingForHealth() {
        when(lifecycle.start(registry.find(BASIC).get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(true);
        var result = handle("start " + BASIC);
        assertThat(result).contains("Started " + BASIC, "ready");
    }

    @Test
    void reportsStartedModuleThatNeverBecomesReady() {
        when(lifecycle.start(registry.find(BASIC).get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(false);
        var result = handle("start " + BASIC);
        assertThat(result).contains("Started " + BASIC, "not ready");
    }

    @Test
    void reportsProgressWhileModuleStarts() {
        when(lifecycle.start(registry.find(BASIC).get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(true);
        List<String> tokens = new ArrayList<>();
        var result = sut.handle("start " + BASIC, tokens::add);
        assertThat(tokens).containsExactly("Waiting for " + BASIC + " to become ready...\n");
        assertThat(result).contains("Started " + BASIC, "ready");
    }

    @Test
    void stopsModule() {
        when(lifecycle.stop(BASIC)).thenReturn(true);
        var result = handle("stop " + BASIC);
        assertThat(result).contains("Stopped " + BASIC);
    }

    @Test
    void startCancelledPromptIsNoOp() {
        when(prompter.pick(eq("Start module"), anyList())).thenReturn(Optional.empty());
        var result = handle("start");
        assertThat(result).isEmpty();
        verify(lifecycle, never()).start(any());
    }

    @Test
    void reportsModuleAlreadyRunning() {
        when(lifecycle.start(registry.find(BASIC).get())).thenReturn(false);
        var result = handle("start " + BASIC);
        assertThat(result).contains("Module already running: " + BASIC);
    }

    @Test
    void stopCancelledPromptIsNoOp() {
        when(prompter.pick(eq("Stop module"), anyList())).thenReturn(Optional.empty());
        var result = handle("stop");
        assertThat(result).isEmpty();
        verify(lifecycle, never()).stop(any());
    }

    @Test
    void stopPromptsAndStopsChosenModule() {
        when(prompter.pick(eq("Stop module"), anyList())).thenReturn(Optional.of(BASIC));
        when(lifecycle.stop(BASIC)).thenReturn(true);
        var result = handle("stop");
        assertThat(result).contains("Stopped " + BASIC);
    }

    @Test
    void reportsStopWhenModuleNotRunning() {
        when(lifecycle.stop(BASIC)).thenReturn(false);
        var result = handle("stop " + BASIC);
        assertThat(result).contains("Module not running: " + BASIC);
    }

    @Test
    void marksChildStartedModuleAsRunningWithChildOrigin() {
        when(lifecycle.isRunning(BASIC)).thenReturn(true);
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        var result = handle("modules");
        assertThat(result).contains(BASIC, "running (child)");
    }
}