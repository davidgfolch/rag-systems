package com.rag.tui.ui;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestStatusDTO;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.ChatMessageDTO;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.common.services.FileDocumentLoader;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandDispatcherTest {

    private final ModuleRegistry registry = new ModuleRegistry(
            List.of(new Module("rag-basic", "http://localhost:8081"),
                    new Module("rag-advanced", "http://localhost:8082")),
            "rag-basic");
    private final ModuleLifecycleManager lifecycle = mock(ModuleLifecycleManager.class);
    private final RagApiClient apiClient = mock(RagApiClient.class);
    private final ChatGateway chatGateway = mock(ChatGateway.class);
    private final MemoryClient memoryClient = mock(MemoryClient.class);
    private final FileDocumentLoader fileLoader = mock(FileDocumentLoader.class);
    private final ModuleHealthClient healthClient = mock(ModuleHealthClient.class);
    private final CommandDispatcher sut = new CommandDispatcher(registry, lifecycle,
            new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient, fileLoader, healthClient),
            new CommandDispatcher.Settings(10_000, 4));

    private CommandResult handle(String input) {
        return sut.handle(input, token -> {});
    }

    @Test
    void listsModulesWithActiveAndState() {
        when(lifecycle.isRunning("rag-basic")).thenReturn(true);

        CommandResult result = handle("modules");

        assertThat(result.message())
                .contains("rag-basic", "running", "(active)")
                .contains("rag-advanced", "stopped");
        assertThat(result.exit()).isFalse();
    }

    @Test
    void marksExternallyStartedModuleAsRunning() {
        when(lifecycle.isRunning("rag-advanced")).thenReturn(false);
        when(healthClient.isUp("http://localhost:8082")).thenReturn(true);

        CommandResult result = handle("modules");

        assertThat(result.message()).contains("rag-advanced", "running (external)");
    }

    @Test
    void listsDocumentsFromReachableModules() {
        when(healthClient.isUp("http://localhost:8081")).thenReturn(true);
        when(healthClient.isUp("http://localhost:8082")).thenReturn(false);
        when(apiClient.listDocuments("http://localhost:8081")).thenReturn(List.of(
                new DocumentSummaryDTO().documentId("d1").chunkCount(3)
                        .putMetadataItem("fileName", "note.txt")));

        CommandResult result = handle("documents");

        assertThat(result.message())
                .contains("rag-basic")
                .contains("d1", "3 chunks", "[note.txt]")
                .doesNotContain("rag-advanced");
    }

    @Test
    void reportsNoReachableModulesForDocuments() {
        when(healthClient.isUp(anyString())).thenReturn(false);

        CommandResult result = handle("documents");

        assertThat(result.message()).contains("No rag-* modules are reachable");
    }

    @Test
    void switchesActiveModule() {
        CommandResult result = handle("use rag-advanced");

        assertThat(result.message()).contains("Active module: rag-advanced");
        assertThat(registry.active().name()).isEqualTo("rag-advanced");
    }

    @Test
    void rejectsUnknownModule() {
        CommandResult result = handle("use nope");

        assertThat(result.message()).contains("Unknown module");
    }

    @Test
    void startsModuleWaitingForHealth() {
        when(lifecycle.start(registry.find("rag-basic").get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(true);

        CommandResult result = handle("start rag-basic");

        assertThat(result.message()).contains("Started rag-basic", "ready");
    }

    @Test
    void reportsStartedModuleThatNeverBecomesReady() {
        when(lifecycle.start(registry.find("rag-basic").get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(false);

        CommandResult result = handle("start rag-basic");

        assertThat(result.message()).contains("Started rag-basic", "not ready");
    }

    @Test
    void reportsProgressWhileModuleStarts() {
        when(lifecycle.start(registry.find("rag-basic").get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(true);

        List<String> tokens = new ArrayList<>();
        CommandResult result = sut.handle("start rag-basic", tokens::add);

        assertThat(tokens).containsExactly("Waiting for rag-basic to become ready...\n");
        assertThat(result.message()).contains("Started rag-basic", "ready");
    }

    @Test
    void stopsModule() {
        when(lifecycle.stop("rag-basic")).thenReturn(true);

        CommandResult result = handle("stop rag-basic");

        assertThat(result.message()).contains("Stopped rag-basic");
    }

    @Test
    void ingestsFileViaActiveModuleAsync() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, java.util.Map.of("fileName", "note.txt")));
        IngestJobResponse job = new IngestJobResponse().documentId("d1");
        when(apiClient.submitIngestFile(bytes, "note.txt", java.util.Map.of("fileName", "note.txt")))
                .thenReturn(job);
        when(apiClient.ingestStatus("d1")).thenReturn(new IngestStatusDTO().documentId("d1")
                .state(IngestStatusDTO.StateEnum.COMPLETED).chunkCount(3));

        List<String> tokens = new ArrayList<>();
        CommandResult result = sut.handle("add-file note.txt", tokens::add);

        assertThat(result.message()).contains("submitted", "d1", "keep typing");
        await(tokens, "complete", 3);
        assertThat(tokens).anyMatch(t -> t.contains("complete") && t.contains("3 chunks"));
    }

    @Test
    void ingestsUrlViaActiveModule() {
        when(apiClient.ingestUrl("https://example.com"))
                .thenReturn(new IngestResponse().documentId("d1").chunkCount(3));

        CommandResult result = handle("add-url https://example.com");

        assertThat(result.message()).contains("d1", "3");
    }

    @Test
    void streamsAskTokens() {
        List<String> tokens = new ArrayList<>();
        when(chatGateway.ask(eq("what is rag"), eq(4), any())).thenAnswer(invocation -> {
            Consumer<String> sink = invocation.getArgument(2);
            sink.accept("tok1");
            sink.accept("tok2");
            return "tok1tok2";
        });

        CommandResult result = sut.handle("ask what is rag", tokens::add);

        assertThat(tokens).containsExactly("tok1", "tok2");
        assertThat(result.exit()).isFalse();
    }

    @Test
    void showsHistoryFromMemory() {
        ConversationDTO conversation = new ConversationDTO().id("c1").title("t1");
        when(memoryClient.conversations()).thenReturn(List.of(conversation));
        when(memoryClient.messages("c1")).thenReturn(List.of(
                new ChatMessageDTO().content("hi")));

        CommandResult result = handle("history");

        assertThat(result.message()).contains("c1", "t1", "1 messages");
    }

    @Test
    void showsEmptyHistory() {
        when(memoryClient.conversations()).thenReturn(List.of());

        CommandResult result = handle("history");

        assertThat(result.message()).contains("No conversations yet");
    }

    @Test
    void quits() {
        CommandResult result = handle("quit");

        assertThat(result.exit()).isTrue();
    }

    @Test
    void rejectsUnknownCommand() {
        CommandResult result = handle("frobnicate");

        assertThat(result.message()).contains("Unknown command");
    }

    @Test
    void reportsUnreachableModuleInsteadOfCrashing() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, java.util.Map.of("fileName", "note.txt")));
        when(apiClient.submitIngestFile(eq(bytes), eq("note.txt"), any()))
                .thenThrow(new RestClientException("Connection refused"));

        CommandResult result = handle("add-file note.txt");

        assertThat(result.message()).contains("Module unreachable", "Connection refused");
        assertThat(result.exit()).isFalse();
    }

    private static void await(List<String> tokens, String needle, int timeoutSeconds) {
        org.awaitility.Awaitility.await().atMost(timeoutSeconds, TimeUnit.SECONDS)
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }

    @Test
    void reportsFileNameErrorsWithoutCrashing() {
        when(fileLoader.load("missing.pdf")).thenThrow(
                new FileDocumentLoader.DocumentLoadException("Failed to read file: missing.pdf", null));

        CommandResult result = handle("add-file missing.pdf");

        assertThat(result.message()).contains("Failed to read file");
        assertThat(result.exit()).isFalse();
    }

    @Test
    void reportsChatErrorsWithoutCrashing() {
        when(chatGateway.ask(eq("hello"), eq(4), any()))
                .thenThrow(new ChatGateway.ChatException("Module ws://localhost:8081/ws/chat unreachable", null));

        CommandResult result = handle("ask hello");

        assertThat(result.message()).contains("Chat error", "unreachable");
        assertThat(result.exit()).isFalse();
    }
}