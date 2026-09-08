package com.rag.tui.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentListerIntegrationTest {

    private StubModuleServer stub;
    private RagApiClient apiClient;
    private ModuleHealthClient healthClient;
    private DocumentLister sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        var registry = new ModuleRegistry(
                List.of(new Module("rag-basic", stub.baseUrl())), "rag-basic");
        apiClient = new RagApiClient(registry, RestClient.builder());
        healthClient = new ModuleHealthClient(RestClient.builder());
        sut = new DocumentLister(registry, apiClient, healthClient);
    }

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.stop();
        }
    }

    @Test
    void listsDocumentsFromReachableModule() throws Exception {
        stub.documents(new ObjectMapper().writeValueAsString(List.of(
                new DocumentSummaryDTO().documentId("d1").title("note.txt").chunkCount(3))));

        var result = sut.list();

        assertThat(result).contains("rag-basic")
                .contains("[d1]")
                .contains("note.txt");
    }

    @Test
    void reportsNoReachableModulesForDocuments() {
        stub.stop();
        stub = null;

        var result = sut.list();

        assertThat(result).contains("No rag-* modules are reachable");
    }
}
