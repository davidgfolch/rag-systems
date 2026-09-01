package com.rag.tui.ui;

import com.rag.contract.model.Conversation;
import com.rag.contract.model.IngestResponse;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.services.FileDocumentLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private final CommandDispatcher sut = new CommandDispatcher(
            registry, lifecycle, apiClient, chatGateway, memoryClient, fileLoader, 4);

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
    void startsModule() {
        when(lifecycle.start(registry.find("rag-basic").get())).thenReturn(true);

        CommandResult result = handle("start rag-basic");

        assertThat(result.message()).contains("Started rag-basic");
    }

    @Test
    void stopsModule() {
        when(lifecycle.stop("rag-basic")).thenReturn(true);

        CommandResult result = handle("stop rag-basic");

        assertThat(result.message()).contains("Stopped rag-basic");
    }

    @Test
    void ingestsFileViaActiveModule() {
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile("text", java.util.Map.of()));
        when(apiClient.ingest("text", java.util.Map.of()))
                .thenReturn(new IngestResponse().documentId("d1").chunkCount(3));

        CommandResult result = handle("add-file note.txt");

        assertThat(result.message()).contains("d1", "3");
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
        Conversation conversation = new Conversation().id("c1").title("t1");
        when(memoryClient.conversations()).thenReturn(List.of(conversation));
        when(memoryClient.messages("c1")).thenReturn(List.of(
                new com.rag.contract.model.ChatMessage().content("hi")));

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
}