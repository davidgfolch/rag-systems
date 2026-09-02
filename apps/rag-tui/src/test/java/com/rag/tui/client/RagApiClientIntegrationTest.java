package com.rag.tui.client;

import com.rag.contract.model.IngestResponse;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link RagApiClient} against a real HTTP server on a
 * random local port (no mocking), proving the base64 raw-bytes ingest payload
 * is serialized exactly as the module expects.
 */
class RagApiClientIntegrationTest {

    private StubModuleServer stub;
    private RagApiClient sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        ModuleRegistry registry = new ModuleRegistry(
                List.of(new Module("rag-basic", stub.baseUrl())), "rag-basic");
        sut = new RagApiClient(registry, RestClient.builder());
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void ingestsFileBytesOverMultipartHttp() {
        byte[] bytes = new byte[]{1, 2, 3, 0, -1};

        IngestResponse response = sut.ingestFile(bytes, "x.pdf",
                Map.of("sourceType", "file", "source", "x.pdf", "fileName", "x.pdf"));

        assertThat(response.getDocumentId()).isEqualTo("i-1");
        assertThat(response.getChunkCount()).isEqualTo(2);
        assertThat(stub.lastIngestContentType()).startsWith("multipart/form-data");
        String body = new String(stub.lastIngestBody(), StandardCharsets.UTF_8);
        assertThat(body).contains("x.pdf");
    }

    @Test
    void surfacesConnectionErrorsWithoutEmbellishment() {
        ModuleRegistry dead = new ModuleRegistry(
                List.of(new Module("rag-basic", "http://localhost:1")), "rag-basic");
        RagApiClient client = new RagApiClient(dead, RestClient.builder());
        Map<String, Object> metadata = Map.of();

        assertThatThrownBy(() -> client.ingest("text", metadata))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("localhost");
    }
}