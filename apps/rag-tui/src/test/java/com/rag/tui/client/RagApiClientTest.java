package com.rag.tui.client;

import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.QueryResponse;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.testfixture.TestModules;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static com.rag.contract.constants.ApiPaths.DOCUMENTS;
import static com.rag.contract.constants.ApiPaths.INGEST_FILE_ASYNC;
import static com.rag.contract.constants.ApiPaths.QUERY;

class RagApiClientTest {

    private final ModuleRegistry registry = new ModuleRegistry(
            List.of(TestModules.basic()), TestModules.BASIC);
    private final RestClient.Builder builder = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory());
    private final RagApiClient sut = new RagApiClient(registry, builder, builder);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void submitsFileForAsyncIngestionViaMultipart() {
        byte[] bytes = new byte[]{1, 2, 3};
        server.expect(requestTo(TestModules.BASIC_URL + INGEST_FILE_ASYNC))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MULTIPART_FORM_DATA))
                .andRespond(withSuccess("{\"documentId\":\"d1\"}", APPLICATION_JSON));
        IngestJobResponse res = sut.submitIngestFile(bytes, "doc.pdf", Map.of("sourceType", "file"));
        assertThat(res.getDocumentId()).isEqualTo("d1");
        server.verify();
    }

    @Test
    void queriesActiveModule() {
        server.expect(requestTo(TestModules.BASIC_URL + QUERY))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"question\":\"q\",\"results\":[]}", APPLICATION_JSON));
        QueryResponse res = sut.query("q", 5);
        assertThat(res.getQuestion()).isEqualTo("q");
        server.verify();
    }

    @Test
    void listsDocumentsFromExplicitBaseUrl() {
        server.expect(requestTo(TestModules.BASIC_URL + DOCUMENTS))
                .andExpect(method(GET))
                .andRespond(withSuccess("[{\"documentId\":\"d1\",\"chunkCount\":3,\"metadata\":{\"fileName\":\"n.txt\"}}]",
                        APPLICATION_JSON));
        List<DocumentSummaryDTO> documents = sut.listDocuments(TestModules.BASIC_URL);
        assertThat(documents).hasSize(1);
        DocumentSummaryDTO doc = documents.getFirst();
        assertThat(doc.getDocumentId()).isEqualTo("d1");
        assertThat(doc.getChunkCount()).isEqualTo(3);
        assertThat(doc.getMetadata()).containsEntry("fileName", "n.txt");
        server.verify();
    }

    @Test
    void deletesDocumentOnActiveModule() {
        server.expect(requestTo(TestModules.BASIC_URL + DOCUMENTS + "/d1"))
                .andExpect(method(DELETE))
                .andRespond(withNoContent());
        sut.deleteDocument("d1");
        server.verify();
    }

    @Test
    void deletesDocumentFromExplicitBaseUrl() {
        server.expect(requestTo(TestModules.BASIC_URL + DOCUMENTS + "/d1"))
                .andExpect(method(DELETE))
                .andRespond(withNoContent());
        sut.deleteDocument(TestModules.BASIC_URL, "d1");
        server.verify();
    }

    @Test
    void listDocumentsReturnsEmptyWhenModuleHasNoDocumentApi() {
        server.expect(requestTo(TestModules.PROVIDER_URL + DOCUMENTS))
                .andExpect(method(GET))
                .andRespond(withStatus(NOT_FOUND));
        List<DocumentSummaryDTO> documents = sut.listDocuments(TestModules.PROVIDER_URL);
        assertThat(documents).isEmpty();
        server.verify();
    }
}