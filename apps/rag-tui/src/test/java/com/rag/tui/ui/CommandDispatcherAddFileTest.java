package com.rag.tui.ui;

import com.rag.common.core.services.FileDocumentLoader;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestStatusDTO;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.rag.tui.testfixture.TestDocumentSummaries.withHash;
import static com.rag.tui.testfixture.TestDocumentSummaries.withId;
import static com.rag.tui.testfixture.TestModules.BASIC_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherAddFileTest extends AbstractCommandDispatcherTest {

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
    void addFileSkipsSubmissionWhenContentHashAlreadyIngested() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "note.txt", "contentHash", "abc")));
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withHash("d1", "old.txt", "abc")));
        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-file note.txt", tokens::add);
        assertThat(result).contains("already ingested", "d1");
        verify(apiClient, never()).submitIngestFile(any(), anyString(), any());
    }

    @Test
    void addFileResubmitsWhenOverrideConfirmed() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "note.txt", "contentHash", "abc")));
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withHash("d1", "old.txt", "abc")));
        when(prompter.confirm(anyString())).thenReturn(true);
        when(apiClient.submitIngestFile(eq(bytes), eq("note.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("d2"));
        var result = handle("add-file note.txt");
        assertThat(result).contains("d2");
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
        verify(apiClient).submitIngestFile(eq(bytes), eq("note.txt"), any());
    }

    @Test
    void addFileSkipsSubmissionWhenOverrideDeclined() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "note.txt", "contentHash", "abc")));
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withHash("d1", "old.txt", "abc")));
        when(prompter.confirm(anyString())).thenReturn(false);
        var result = handle("add-file note.txt");
        assertThat(result).contains("Skipped");
        verify(apiClient, never()).submitIngestFile(any(), anyString(), any());
    }

    @Test
    void addFileSubmitsWithoutDedupCheckWhenReachableModuleHasNoMetadata() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.load("note.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "note.txt", "contentHash", "abc")));
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withId("d1")));
        when(apiClient.submitIngestFile(eq(bytes), eq("note.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("d2"));
        var result = handle("add-file note.txt");
        assertThat(result).contains("d2");
        verify(prompter, never()).confirm(anyString());
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

    @Test
    void reportsFileNameErrorsWithoutCrashing() {
        when(fileLoader.load("missing.pdf")).thenThrow(
                new FileDocumentLoader.DocumentLoadException("Failed to read file: missing.pdf", null));
        var result = handle("add-file missing.pdf");
        assertThat(result).contains("Failed to read file");
    }

    @Test
    void addFileCancelledPromptShowsUsage() {
        when(prompter.prompt("File path: ")).thenReturn(null);
        var result = handle("add-file");
        assertThat(result).isEqualTo("Usage: add-file <path>");
        verify(fileLoader, never()).load(anyString());
    }

    @Test
    void addFileReportsFailedIngestion() {
        byte[] bytes = new byte[]{1};
        when(fileLoader.load("bad.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "bad.txt")));
        when(apiClient.submitIngestFile(eq(bytes), eq("bad.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("d9"));
        when(apiClient.ingestStatus("d9")).thenReturn(new IngestStatusDTO().documentId("d9")
                .state(IngestStatusDTO.StateEnum.FAILED).message("extraction failed"));
        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-file bad.txt", tokens::add);
        assertThat(result).contains("submitted", "d9");
        await(tokens, "failed", 3);
        assertThat(tokens).anyMatch(t -> t.contains("failed") && t.contains("extraction failed"));
    }

    @Test
    void addFileReportsIngestStatusCheckFailure() {
        byte[] bytes = new byte[]{1};
        when(fileLoader.load("x.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "x.txt")));
        when(apiClient.submitIngestFile(eq(bytes), eq("x.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("d8"));
        when(apiClient.ingestStatus("d8")).thenThrow(new RestClientException("module went away"));
        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-file x.txt", tokens::add);
        assertThat(result).contains("submitted", "d8");
        await(tokens, "could not be checked", 3);
    }

    @Test
    void addFilePollingPropagatesTraceSpan() {
        byte[] bytes = new byte[]{1};
        when(fileLoader.load("t.txt"))
                .thenReturn(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "t.txt")));
        when(apiClient.submitIngestFile(eq(bytes), eq("t.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("d7"));
        when(apiClient.ingestStatus("d7")).thenReturn(new IngestStatusDTO().documentId("d7")
                .state(IngestStatusDTO.StateEnum.COMPLETED).chunkCount(1));
        var tracer = new SimpleTracer();
        var dispatcher = new CommandDispatcher(registry, lifecycle,
                new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient, fileLoader, healthClient,
                        providerClient),
                new CommandDispatcher.Settings(10_000, 4, 60), commandRegistry, prompter, tracer);
        List<String> tokens = new ArrayList<>();
        dispatcher.handle("add-file t.txt", tokens::add);
        await(tokens, "complete", 3);
    }
}