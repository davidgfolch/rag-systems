package com.rag.tui.client;

import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.QueryResponse;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RagApiClientTest {

    private final ModuleRegistry registry = new ModuleRegistry(
            List.of(new Module("rag-basic", "http://localhost:8081")), "rag-basic");
    private final RestClient.Builder builder = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory());
    private final RagApiClient sut = new RagApiClient(registry, builder);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void ingestsContent() {
        server.expect(requestTo("http://localhost:8081/api/documents/ingest"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"documentId\":\"d1\",\"chunkCount\":2}",
                        MediaType.APPLICATION_JSON));

        IngestResponse response = sut.ingest("text", Map.of("k", "v"));

        assertThat(response.getDocumentId()).isEqualTo("d1");
        assertThat(response.getChunkCount()).isEqualTo(2);
        server.verify();
    }

    @Test
    void ingestsFileViaMultipart() {
        byte[] bytes = new byte[]{1, 2, 3};
        server.expect(requestTo("http://localhost:8081/api/documents/ingest-file"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess("{\"documentId\":\"d1\",\"chunkCount\":5}",
                        MediaType.APPLICATION_JSON));

        IngestResponse response = sut.ingestFile(bytes, "doc.pdf", Map.of("sourceType", "file"));

        assertThat(response.getDocumentId()).isEqualTo("d1");
        assertThat(response.getChunkCount()).isEqualTo(5);
        server.verify();
    }

    @Test
    void ingestsUrl() {
        server.expect(requestTo("http://localhost:8081/api/documents/ingest-url"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"documentId\":\"d1\",\"chunkCount\":3}",
                        MediaType.APPLICATION_JSON));

        IngestResponse response = sut.ingestUrl("https://example.com");

        assertThat(response.getDocumentId()).isEqualTo("d1");
        server.verify();
    }

    @Test
    void queriesActiveModule() {
        server.expect(requestTo("http://localhost:8081/api/query"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"question\":\"q\",\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        QueryResponse response = sut.query("q", 5);

        assertThat(response.getQuestion()).isEqualTo("q");
        server.verify();
    }
}