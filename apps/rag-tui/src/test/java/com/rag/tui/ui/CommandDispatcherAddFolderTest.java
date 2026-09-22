package com.rag.tui.ui;

import com.rag.common.core.services.FileDocumentLoader;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestStatusDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.rag.tui.testfixture.TestDocumentSummaries.withHash;
import static com.rag.tui.testfixture.TestModules.BASIC_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherAddFolderTest extends AbstractCommandDispatcherTest {

    @Test
    void addFolderOverridesDuplicateWhenConfirmed() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.loadFolder("notes"))
                .thenReturn(List.of(new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "a.txt", "contentHash", "abc"))));
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withHash("d1", "old.txt", "abc")));
        when(prompter.confirm(anyString())).thenReturn(true);
        when(apiClient.submitIngestFile(eq(bytes), eq("a.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("db"));
        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-folder notes", tokens::add);
        assertThat(result).contains("Submitted 1 of 1 files");
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
        verify(apiClient).submitIngestFile(eq(bytes), eq("a.txt"), any());
    }

    @Test
    void addFolderSkipsFileWhenContentHashAlreadyIngested() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(fileLoader.loadFolder("notes")).thenReturn(List.of(
                new FileDocumentLoader.LoadedFile(bytes, Map.of("fileName", "a.txt", "contentHash", "abc")),
                new FileDocumentLoader.LoadedFile(new byte[]{9}, Map.of("fileName", "b.txt", "contentHash", "def"))));
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withHash("d1", "old.txt", "abc")));
        when(apiClient.submitIngestFile(eq(new byte[]{9}), eq("b.txt"), any()))
                .thenReturn(new IngestJobResponse().documentId("db"));
        List<String> tokens = new ArrayList<>();
        var result = sut.handle("add-folder notes", tokens::add);
        assertThat(result).contains("Submitted 1 of 2 files", "1 duplicate");
        verify(apiClient, never()).submitIngestFile(eq(bytes), eq("a.txt"), any());
        verify(apiClient).submitIngestFile(eq(new byte[]{9}), eq("b.txt"), any());
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
        assertThat(result).contains("Submitted 2 of 2 files", "notes");
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
}