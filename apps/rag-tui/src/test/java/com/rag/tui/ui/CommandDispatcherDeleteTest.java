package com.rag.tui.ui;

import com.rag.contract.model.DocumentSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.rag.tui.testfixture.TestDocumentSummaries.withId;
import static com.rag.tui.testfixture.TestModules.ADVANCED;
import static com.rag.tui.testfixture.TestModules.ADVANCED_URL;
import static com.rag.tui.testfixture.TestModules.BASIC;
import static com.rag.tui.testfixture.TestModules.BASIC_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherDeleteTest extends AbstractCommandDispatcherTest {

    @Test
    void deletesDocumentFromItsModuleWhenActiveModuleHasIt() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withId("d1")));
        var result = handle("delete d1");
        assertThat(result).contains("Deleted document d1", BASIC);
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
    }

    @Test
    void deletesDocumentFromNonActiveModule() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(healthClient.isUp(ADVANCED_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of());
        when(apiClient.listDocuments(ADVANCED_URL)).thenReturn(List.of(withId("d1")));
        var result = handle("delete d1");
        assertThat(result).contains("Deleted document d1", ADVANCED);
        verify(apiClient).deleteDocument(ADVANCED_URL, "d1");
    }

    @Test
    void deleteWithoutArgumentCancelsWhenNoReachableDocuments() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(false);
        when(healthClient.isUp(ADVANCED_URL)).thenReturn(false);
        var result = handle("delete");
        assertThat(result).isEmpty();
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void deletePromptsAndDeletesChosenDocument() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withId("d1")));
        when(apiClient.listDocuments(ADVANCED_URL)).thenReturn(List.of());
        when(prompter.pick(eq("Delete document"), anyList())).thenReturn(Optional.of("d1"));
        var result = handle("delete");
        assertThat(result).contains("Deleted document d1", BASIC);
        verify(apiClient).deleteDocument(BASIC_URL, "d1");
    }

    @Test
    void deleteReportsDocumentNotFoundOnReachableModules() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(List.of(withId("other")));
        var result = handle("delete d1");
        assertThat(result).contains("Document d1 not found");
        verify(apiClient, never()).deleteDocument(anyString(), anyString());
    }

    @Test
    void deleteSubtitleHandlesNullChunkCount() {
        when(healthClient.isUp(BASIC_URL)).thenReturn(true);
        when(apiClient.listDocuments(BASIC_URL)).thenReturn(
                List.of(new DocumentSummaryDTO().documentId("d1").title("note.txt")));
        when(prompter.pick(eq("Delete document"), anyList())).thenReturn(Optional.of("d1"));
        var result = handle("delete");
        assertThat(result).contains("Deleted document d1");
    }
}