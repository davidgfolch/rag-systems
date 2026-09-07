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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private final CommandRegistry commandRegistry = new CommandRegistry();
    private final CommandDispatcher sut = new CommandDispatcher(registry, lifecycle,
            new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient, fileLoader, healthClient),
            new CommandDispatcher.Settings(10_000, 4, 60), commandRegistry);

    private String handle(String input) {
        return sut.handle(input, token -> {});
    }

    @Test
    void listsModulesWithActiveAndState() {
        when(lifecycle.isRunning("rag-basic")).thenReturn(true);

        var result = handle("modules");

        assertThat(result)
                .contains("rag-basic", "running", "(active)")
                .contains("rag-advanced", "stopped");
    }

    @Test
    void marksExternallyStartedModuleAsRunning() {
        when(lifecycle.isRunning("rag-advanced")).thenReturn(false);
        when(healthClient.isUp("http://localhost:8082")).thenReturn(true);

        var result = handle("modules");

        assertThat(result).contains("rag-advanced", "running (external)");
    }

    @Test
    void listsDocumentsFromReachableModules() {
        when(healthClient.isUp("http://localhost:8081")).thenReturn(true);
        when(healthClient.isUp("http://localhost:8082")).thenReturn(false);
        when(apiClient.listDocuments("http://localhost:8081")).thenReturn(List.of(
                new DocumentSummaryDTO().documentId("d1").title("note.txt").chunkCount(3)));

        var result = handle("documents");

        assertThat(result)
                .contains("rag-basic")
                .contains("note.txt", "3 chunks", "[d1]")
                .doesNotContain("rag-advanced");
    }

    @Test
    void reportsNoReachableModulesForDocuments() {
        when(healthClient.isUp(anyString())).thenReturn(false);

        var result = handle("documents");

        assertThat(result).contains("No rag-* modules are reachable");
    }

    @Test
    void switchesActiveModule() {
        var result = handle("use rag-advanced");

        assertThat(result).contains("Active module: rag-advanced");
        assertThat(registry.active().name()).isEqualTo("rag-advanced");
    }

    @Test
    void rejectsUnknownModule() {
        var result = handle("use nope");

        assertThat(result).contains("Unknown module");
    }

    @Test
    void startsModuleWaitingForHealth() {
        when(lifecycle.start(registry.find("rag-basic").get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(true);

        var result = handle("start rag-basic");

        assertThat(result).contains("Started rag-basic", "ready");
    }

    @Test
    void reportsStartedModuleThatNeverBecomesReady() {
        when(lifecycle.start(registry.find("rag-basic").get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(false);

        var result = handle("start rag-basic");

        assertThat(result).contains("Started rag-basic", "not ready");
    }

    @Test
    void reportsProgressWhileModuleStarts() {
        when(lifecycle.start(registry.find("rag-basic").get())).thenReturn(true);
        when(healthClient.waitUntilUp(anyString(), anyLong(), any())).thenReturn(true);

        List<String> tokens = new ArrayList<>();
        var result = sut.handle("start rag-basic", tokens::add);

        assertThat(tokens).containsExactly("Waiting for rag-basic to become ready...\n");
        assertThat(result).contains("Started rag-basic", "ready");
    }

    @Test
    void stopsModule() {
        when(lifecycle.stop("rag-basic")).thenReturn(true);

        var result = handle("stop rag-basic");

        assertThat(result).contains("Stopped rag-basic");
    }

    @Test
    void ingestsFileViaActiveModuleAsync() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "note.txt")));
        IngestJobResponse job = new IngestJobResponse().documentId("d1");
        when(apiClient.submitIngestFile(bytes, "note.txt", Map.of("fileName", "note.txt")))
                .thenReturn(job);
        when(apiClient.ingestStatus("d1")).thenReturn(new IngestStatusDTO().documentId("d1")
                .state(IngestStatusDTO.StateEnum.COMPLETED).chunkCount(3));

        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-file note.txt", tokens::add);

        assertThat(result).contains("submitted", "d1", "keep typing");
        await(tokens, "complete", 3);
        assertThat(tokens).anyMatch(t -> t.contains("complete") && t.contains("3 chunks"));
    }

    @Test
    void ingestsUrlViaActiveModule() {
        when(apiClient.ingestUrl("https://example.com"))
                .thenReturn(new IngestResponse().documentId("d1").chunkCount(3));

        var result = handle("add-url https://example.com");

        assertThat(result).contains("d1", "3");
    }

    @Test
    void deletesDocumentFromItsModuleWhenActiveModuleHasIt() {
        when(healthClient.isUp("http://localhost:8081")).thenReturn(true);
        when(apiClient.listDocuments("http://localhost:8081")).thenReturn(List.of(
                new DocumentSummaryDTO().documentId("d1").title("note.txt").chunkCount(3)));

        var result = handle("delete d1");

        assertThat(result).contains("Deleted document d1", "rag-basic");
        verify(apiClient).deleteDocument("http://localhost:8081", "d1");
    }

    @Test
    void deletesDocumentFromNonActiveModule() {
        when(healthClient.isUp("http://localhost:8081")).thenReturn(true);
        when(healthClient.isUp("http://localhost:8082")).thenReturn(true);
        when(apiClient.listDocuments("http://localhost:8081")).thenReturn(List.of());
        when(apiClient.listDocuments("http://localhost:8082")).thenReturn(List.of(
                new DocumentSummaryDTO().documentId("d1").title("note.txt").chunkCount(3)));

        var result = handle("delete d1");

        assertThat(result).contains("Deleted document d1", "rag-advanced");
        verify(apiClient).deleteDocument("http://localhost:8082", "d1");
    }

    @Test
    void deleteWithoutArgumentShowsUsage() {
        var result = handle("delete");

        assertThat(result).contains("Usage: delete <document-id>");
        verify(apiClient, never()).deleteDocument(anyString());
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void deleteReportsDocumentNotFoundOnReachableModules() {
        when(healthClient.isUp("http://localhost:8081")).thenReturn(true);
        when(apiClient.listDocuments("http://localhost:8081")).thenReturn(List.of(
                new DocumentSummaryDTO().documentId("other").title("note.txt").chunkCount(3)));

        var result = handle("delete d1");

        assertThat(result).contains("Document d1 not found");
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void ingestsFolderSubmittingEachFileAsync() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.loadFolder("notes"))
                .thenReturn(List.of(
                        new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "a.txt")),
                        new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "b.txt"))));
        when(apiClient.submitIngestFile(eq(bytes), eq("a.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("da"));
        when(apiClient.submitIngestFile(eq(bytes), eq("b.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("db"));
        when(apiClient.ingestStatus(anyString())).thenReturn(new IngestStatusDTO().documentId("da")
                .state(IngestStatusDTO.StateEnum.COMPLETED).chunkCount(2));

        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-folder notes", tokens::add);

        assertThat(result).contains("Submitted 2 files", "notes");
        verify(apiClient).submitIngestFile(eq(bytes), eq("a.txt"), any());
        verify(apiClient).submitIngestFile(eq(bytes), eq("b.txt"), any());
        await(tokens, "complete", 3);
    }

    @Test
    void addFolderWithoutArgumentShowsUsage() {
        var result = handle("add-folder");

        assertThat(result).contains("Usage: add-folder <path>");
        verify(fileLoader, never()).loadFolder(anyString());
    }

    @Test
    void addFolderReportsEmptyFolder() {
        when(fileLoader.loadFolder("empty")).thenReturn(List.of());

        var result = handle("add-folder empty");

        assertThat(result).contains("No ingestible files found");
    }

    @Test
    void addFolderReportsUnreadableFolder() {
        when(fileLoader.loadFolder("missing"))
                .thenThrow(new FileDocumentLoader.DocumentLoadException("Failed to read folder: missing", null));

        var result = handle("add-folder missing");

        assertThat(result).contains("Failed to read folder");
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

        var result = sut.handle("ask what is rag", tokens::add);

        assertThat(tokens).containsExactly("tok1", "tok2");
    }

    @Test
    void showsHistoryFromMemory() {
        ConversationDTO conversation = new ConversationDTO().id("c1").title("t1");
        when(memoryClient.conversations()).thenReturn(List.of(conversation));
        when(memoryClient.messages("c1")).thenReturn(List.of(
                new ChatMessageDTO().content("hi")));

        var result = handle("history");

        assertThat(result).contains("c1", "t1", "1 messages");
    }

    @Test
    void showsEmptyHistory() {
        when(memoryClient.conversations()).thenReturn(List.of());

        var result = handle("history");

        assertThat(result).contains("No conversations yet");
    }

    @Test
    void quits() {
        assertThatThrownBy(() -> handle("quit"))
                .isInstanceOf(ShellExitException.class);
    }

    @Test
    void rejectsUnknownCommand() {
        var result = handle("frobnicate");

        assertThat(result).contains("Unknown command");
    }

    @Test
    void reportsUnreachableModuleInsteadOfCrashing() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "note.txt")));
        when(apiClient.submitIngestFile(eq(bytes), eq("note.txt"), any()))
                .thenThrow(new RestClientException("Connection refused"));

        var result = handle("add-file note.txt");

        assertThat(result).contains("Module unreachable", "Connection refused");
    }

    private static void await(List<String> tokens, String needle, int timeoutSeconds) {
        Awaitility.await().atMost(timeoutSeconds, TimeUnit.SECONDS)
                .until(() -> tokens.stream().anyMatch(t -> t.contains(needle)));
    }

    @Test
    void reportsFileNameErrorsWithoutCrashing() {
        when(fileLoader.load("missing.pdf")).thenThrow(
                new FileDocumentLoader.DocumentLoadException("Failed to read file: missing.pdf", null));

        var result = handle("add-file missing.pdf");

        assertThat(result).contains("Failed to read file");
    }

    @Test
    void reportsChatErrorsWithoutCrashing() {
        when(chatGateway.ask(eq("hello"), eq(4), any()))
                .thenThrow(new ChatGateway.ChatException("Module ws://localhost:8081/ws/chat unreachable", null));

        var result = handle("ask hello");

        assertThat(result).contains("Chat error", "unreachable");
    }
}
