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

import static com.rag.tui.ui.DocumentLister.NO_DOCUMENTS;
import static com.rag.tui.ui.DocumentLister.RAG_MODULES_DOWN;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentListerIntegrationTest {

    public static final DocumentSummaryDTO DOC_SUMMARY = new DocumentSummaryDTO().documentId("d1").title("note.txt").chunkCount(3);
    public static final String RAG_BASIC = "rag-basic";
    private StubModuleServer stub;
    private DocumentLister sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        var registry = new ModuleRegistry(
                List.of(new Module(RAG_BASIC, stub.baseUrl())), RAG_BASIC);
        sut = new DocumentLister(registry,
                new RagApiClient(registry, RestClient.builder()),
                new ModuleHealthClient(RestClient.builder()));
    }

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.stop();
        }
    }

    @Test
    void listsDocumentsFromReachableModule() throws Exception {
        stub.documents(new ObjectMapper().writeValueAsString(List.of(DOC_SUMMARY)));
        assertThat(sut.list()).contains(RAG_BASIC)
                .contains("[d1]")
                .contains("note.txt");
    }

    @Test
    void reportsNoReachableModulesForDocuments() {
        stub.stop();
        stub = null;
        assertThat(sut.list()).isEqualTo(RAG_MODULES_DOWN);
    }

    @Test
    void skipsModulesWithNoDocuments() {
        stub.documents("[]");
        assertThat(sut.list()).contains(NO_DOCUMENTS);
    }
}
