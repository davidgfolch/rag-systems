package com.rag.tui.client;

import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.QueryResponse;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.testfixture.TestModules;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static com.rag.contract.constants.ApiPaths.DOCUMENTS;
import static com.rag.contract.constants.ApiPaths.INGEST;
import static com.rag.contract.constants.ApiPaths.INGEST_FILE;
import static com.rag.contract.constants.ApiPaths.INGEST_URL;
import static com.rag.contract.constants.ApiPaths.QUERY;

class RagApiClientTest {

    private final ModuleRegistry registry = new ModuleRegistry(
            List.of(TestModules.basic()), TestModules.BASIC);
    private final RestClient.Builder builder = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory());
    private final RagApiClient sut = new RagApiClient(registry, builder);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void ingestsContent() {
        server.expect(requestTo(TestModules.BASIC_URL + INGEST))
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
        server.expect(requestTo(TestModules.BASIC_URL + INGEST_FILE))
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
        server.expect(requestTo(TestModules.BASIC_URL + INGEST_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"documentId\":\"d1\",\"chunkCount\":3}",
                        MediaType.APPLICATION_JSON));

        IngestResponse response = sut.ingestUrl("https://example.com");

        assertThat(response.getDocumentId()).isEqualTo("d1");
        server.verify();
    }

    @Test
    void queriesActiveModule() {
        server.expect(requestTo(TestModules.BASIC_URL + QUERY))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"question\":\"q\",\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        QueryResponse response = sut.query("q", 5);

        assertThat(response.getQuestion()).isEqualTo("q");
        server.verify();
    }

    @Test
    void listsDocumentsFromExplicitBaseUrl() {
        server.expect(requestTo(TestModules.BASIC_URL + DOCUMENTS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"documentId\":\"d1\",\"chunkCount\":3,\"metadata\":{\"fileName\":\"n.txt\"}}]",
                        MediaType.APPLICATION_JSON));

        List<DocumentSummaryDTO> documents = sut.listDocuments(TestModules.BASIC_URL);

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getDocumentId()).isEqualTo("d1");
        assertThat(documents.get(0).getChunkCount()).isEqualTo(3);
        assertThat(documents.get(0).getMetadata()).containsEntry("fileName", "n.txt");
        server.verify();
    }

    @Test
    void deletesDocumentOnActiveModule() {
        server.expect(requestTo(TestModules.BASIC_URL + DOCUMENTS + "/d1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        sut.deleteDocument("d1");

        server.verify();
    }

    @Test
    void deletesDocumentFromExplicitBaseUrl() {
        server.expect(requestTo(TestModules.BASIC_URL + DOCUMENTS + "/d1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        sut.deleteDocument(TestModules.BASIC_URL, "d1");

        server.verify();
    }

    @Test
    void listDocumentsReturnsEmptyWhenModuleHasNoDocumentApi() {
        server.expect(requestTo(TestModules.PROVIDER_URL + DOCUMENTS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        List<DocumentSummaryDTO> documents = sut.listDocuments(TestModules.PROVIDER_URL);

        assertThat(documents).isEmpty();
        server.verify();
    }
}