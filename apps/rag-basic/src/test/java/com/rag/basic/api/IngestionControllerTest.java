package com.rag.basic.api;

import com.rag.basic.services.RetrievalService;
import com.rag.basic.services.WebCrawlerClient;
import com.rag.common.domain.DocumentSummary;
import com.rag.common.domain.MetadataKeys;
import com.rag.common.services.AsyncIngestionService;
import com.rag.common.services.IngestionService;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.PageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.rag.common.testfixture.TestIngestionAssertions.assertBadRequestAndNotIngested;
import static com.rag.common.testfixture.TestIngestionAssertions.assertDocumentCreated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionControllerTest {

    private final IngestionService service = mock(IngestionService.class);
    private final WebCrawlerClient webCrawlerClient = mock(WebCrawlerClient.class);
    private final IngestionController controller = new IngestionController(service, webCrawlerClient);

    @Test
    void ingestsValidRequest() {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 5));
        ResponseEntity<IngestResponse> response =
                controller.ingest(new IngestRequest().content("some content"));
        assertDocumentCreated(response, "d1", 5);
    }

    @Test
    void rejectsBlankContent() {
        ResponseEntity<IngestResponse> response =
                controller.ingest(new IngestRequest().content("   "));
        assertBadRequestAndNotIngested(response, service);
    }

    @Test
    void ingestsBinaryContentViaRawMetadata() {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 7));
        ResponseEntity<IngestResponse> response =
                controller.ingest(new IngestRequest().content("")
                        .metadata(Map.of(MetadataKeys.RAW, Base64.getEncoder().encodeToString(new byte[]{0x25, 0x50, 0x44, 0x46}))));
        assertDocumentCreated(response, "d1", 7);
    }

    @Test
    void ingestsMultipartFile() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 7));
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46});
        ResponseEntity<IngestResponse> response =
                controller.ingestFile(file, Map.of(MetadataKeys.SOURCE, "x.pdf"));
        assertDocumentCreated(response, "d1", 7);
    }

    @Test
    void rejectsEmptyMultipartFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        ResponseEntity<IngestResponse> response =
                controller.ingestFile(file, Map.of());
        assertBadRequestAndNotIngested(response, service);
    }

    @Test
    void rejectsEmptyContentWithoutRawMetadata() {
        ResponseEntity<IngestResponse> response =
                controller.ingest(new IngestRequest().content(""));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).ingest(any());
    }

    @Test
    void ingestsUrlViaWebCrawler() {
        PageDTO page = new PageDTO().url("https://example.com").title("Example").text("page text");
        when(webCrawlerClient.fetch("https://example.com")).thenReturn(page);
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d2", 3));
        ResponseEntity<IngestResponse> response =
                controller.ingestUrl(new IngestUrlRequest().url(URI.create("https://example.com")));
        assertDocumentCreated(response, "d2", 3);
    }

    @Test
    void rejectsBlankUrl() {
        ResponseEntity<IngestResponse> response =
                controller.ingestUrl(new IngestUrlRequest().url(URI.create("")));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).ingest(any());
    }

    @Test
    void submitsFileAsyncReturningAccepted() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("j1", 7));
        IngestionController asyncController = new IngestionController(service, webCrawlerClient,
                new AsyncIngestionService(service), null);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46});
        ResponseEntity<IngestJobResponse> response =
                asyncController.ingestFileAsync(file, Map.of(MetadataKeys.SOURCE, "x.pdf"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getDocumentId()).isNotBlank();
        String documentId = response.getBody().getDocumentId();
        assertThat(awaitState(asyncController, documentId)).isEqualTo(AsyncIngestionService.STATE_COMPLETED);
    }

    @Test
    void rejectsEmptyFileAsync() throws Exception {
        IngestionController asyncController = new IngestionController(service, webCrawlerClient,
                new AsyncIngestionService(service), null);
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        ResponseEntity<IngestJobResponse> response =
                asyncController.ingestFileAsync(file, Map.of());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).ingest(any());
    }

    private static String awaitState(IngestionController asyncController, String documentId) {
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> isTerminalState(asyncController, documentId));
        return asyncController.ingestStatus(documentId).getBody().getState().getValue();
    }

    private static boolean isTerminalState(IngestionController asyncController, String documentId) {
        String state = asyncController.ingestStatus(documentId).getBody().getState().getValue();
        return AsyncIngestionService.STATE_COMPLETED.equals(state) || AsyncIngestionService.STATE_FAILED.equals(state);
    }

    @Test
    void listsDocumentsViaRetrievalService() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        when(retrievalService.listDocuments()).thenReturn(List.of(
                new DocumentSummary("d1", 3, Map.of(MetadataKeys.FILE_NAME, "note.txt", MetadataKeys.TITLE, "note.txt"))));
        IngestionController listController = new IngestionController(service, webCrawlerClient, null, retrievalService);
        ResponseEntity<List<DocumentSummaryDTO>> response = listController.listDocuments();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        DocumentSummaryDTO dto = response.getBody().get(0);
        assertThat(dto.getDocumentId()).isEqualTo("d1");
        assertThat(dto.getTitle()).isEqualTo("note.txt");
        assertThat(dto.getChunkCount()).isEqualTo(3);
        assertThat(dto.getMetadata()).containsEntry(MetadataKeys.FILE_NAME, "note.txt");
    }

    @Test
    void deletesDocumentViaRetrievalService() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        IngestionController deleteController = new IngestionController(service, webCrawlerClient, null, retrievalService);
        ResponseEntity<Void> response = deleteController.deleteDocument("d1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(retrievalService).delete("d1");
    }

    @Test
    void deleteReturnsUnavailableWhenRetrievalServiceMissing() {
        ResponseEntity<Void> response = controller.deleteDocument("d1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}