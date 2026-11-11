package com.rag.common.ingestion.web;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.domain.MetadataKeys;
import com.rag.common.core.repositories.DocumentStorePort;
import com.rag.common.core.resilience.CapacityExceededException;
import com.rag.common.ingestion.AsyncIngestionService;
import com.rag.common.ingestion.IngestionService;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestStatusDTO;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.PageDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.rag.common.ingestion.testfixture.TestIngestionAssertions.assertBadRequestAndNotIngested;
import static com.rag.common.ingestion.testfixture.TestIngestionAssertions.assertDocumentCreated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.*;

class IngestionControllerTest {

    private final IngestionService service = mock(IngestionService.class);
    private final WebPageFetcher webPageFetcher = mock(WebPageFetcher.class);
    private final IngestionController controller = new IngestionController(service, webPageFetcher);

    @Test
    void ingestsValidRequest() {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 5));
        ResponseEntity<IngestResponse> res = controller.ingest(new IngestRequest().content("some content"));
        assertDocumentCreated(res, "d1", 5);
    }

    @Test
    void rejectsBlankContent() {
        ResponseEntity<IngestResponse> res = controller.ingest(new IngestRequest().content("   "));
        assertBadRequestAndNotIngested(res, service);
    }

    @Test
    void ingestsBinaryContentViaRawMetadata() {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 7));
        ResponseEntity<IngestResponse> res = controller.ingest(new IngestRequest().content("")
                .metadata(Map.of(MetadataKeys.RAW, Base64.getEncoder().encodeToString(new byte[]{0x25, 0x50, 0x44, 0x46}))));
        assertDocumentCreated(res, "d1", 7);
    }

    @Test
    void ingestsMultipartFile() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 7));
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46});
        ResponseEntity<IngestResponse> res = controller.ingestFile(file, Map.of(MetadataKeys.SOURCE, "x.pdf"));
        assertDocumentCreated(res, "d1", 7);
    }

    @Test
    void rejectsEmptyMultipartFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        ResponseEntity<IngestResponse> res = controller.ingestFile(file, Map.of());
        assertBadRequestAndNotIngested(res, service);
    }

    @Test
    void ingestFileStoresContentHashComputedFromRawBytesWhenClientOmitsIt() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("d1", 7));
        byte[] bytes = {0x25, 0x50, 0x44, 0x46};
        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", bytes);
        ResponseEntity<IngestResponse> res = controller.ingestFile(file, null);
        assertDocumentCreated(res, "d1", 7);
        var expected = sha256(bytes);
        verify(service).ingest(argThat(d -> expected.equals(d.getMetadata().get(MetadataKeys.CONTENT_HASH))));
    }

    @Test
    void ingestFileAsyncStoresServerComputedContentHashOverClientValue() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("j1", 7));
        IngestionController asyncController = new IngestionController(service, webPageFetcher,
                new AsyncIngestionService(service), null);
        byte[] bytes = {0x25, 0x50, 0x44, 0x46};
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", bytes);
        var res = asyncController.ingestFileAsync(file,
                Map.of(MetadataKeys.CONTENT_HASH, "0000000000000000000000000000000000000000000000000000000000000000"));
        assertThat(res.getStatusCode()).isEqualTo(ACCEPTED);
        awaitState(asyncController, res.getBody().getDocumentId());
        var expected = sha256(bytes);
        verify(service).ingest(argThat(d -> expected.equals(d.getMetadata().get(MetadataKeys.CONTENT_HASH))));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void rejectsEmptyContentWithoutRawMetadata() {
        ResponseEntity<IngestResponse> res = controller.ingest(new IngestRequest().content(""));
        assertThat(res.getStatusCode()).isEqualTo(BAD_REQUEST);
        verify(service, never()).ingest(any());
    }

    @Test
    void ingestsUrlViaWebCrawler() {
        PageDTO page = new PageDTO().url("https://example.com").title("Example").text("page text");
        when(webPageFetcher.fetch("https://example.com")).thenReturn(page);
        when(service.ingestParsed(any())).thenReturn(new IngestionService.IngestionResult("d2", 3));
        ResponseEntity<IngestResponse> res =
                controller.ingestUrl(new IngestUrlRequest().url(URI.create("https://example.com")));
        assertDocumentCreated(res, "d2", 3);
    }

    @Test
    void rejectsBlankUrl() {
        ResponseEntity<IngestResponse> res = controller.ingestUrl(new IngestUrlRequest().url(URI.create("")));
        assertThat(res.getStatusCode()).isEqualTo(BAD_REQUEST);
        verify(service, never()).ingestParsed(any());
    }

    @Test
    void submitsFileAsyncReturningAccepted() throws Exception {
        when(service.ingest(any())).thenReturn(new IngestionService.IngestionResult("j1", 7));
        IngestionController asyncController = new IngestionController(service, webPageFetcher,
                new AsyncIngestionService(service), null);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46});
        ResponseEntity<IngestJobResponse> res =
                asyncController.ingestFileAsync(file, Map.of(MetadataKeys.SOURCE, "x.pdf"));
        assertThat(res.getStatusCode()).isEqualTo(ACCEPTED);
        assertThat(res.getBody().getDocumentId()).isNotBlank();
        String documentId = res.getBody().getDocumentId();
        assertThat(awaitState(asyncController, documentId)).isEqualTo(AsyncIngestionService.STATE_COMPLETED);
    }

    @Test
    void rejectsEmptyFileAsync() throws Exception {
        IngestionController asyncController = new IngestionController(service, webPageFetcher,
                new AsyncIngestionService(service), null);
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        ResponseEntity<IngestJobResponse> res = asyncController.ingestFileAsync(file, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(BAD_REQUEST);
        verify(service, never()).ingest(any());
    }

    @Test
    void mapsCapacityExhaustionToServiceUnavailable() {
        ResponseEntity<String> res =
                controller.handleCapacityExceeded(new CapacityExceededException("capacity exhausted"));
        assertThat(res.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        assertThat(res.getBody()).isEqualTo("capacity exhausted");
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
    void listsDocumentsViaDocumentStore() {
        DocumentStorePort documentStore = mock(DocumentStorePort.class);
        when(documentStore.listDocuments()).thenReturn(List.of(
                new DocumentSummary("d1", 3, Map.of(MetadataKeys.FILE_NAME, "note.txt", MetadataKeys.TITLE, "note.txt"))));
        IngestionController listController = new IngestionController(service, webPageFetcher, null, documentStore);
        ResponseEntity<List<DocumentSummaryDTO>> res = listController.listDocuments();
        assertThat(res.getStatusCode()).isEqualTo(OK);
        assertThat(res.getBody()).hasSize(1);
        DocumentSummaryDTO dto = res.getBody().getFirst();
        assertThat(dto.getDocumentId()).isEqualTo("d1");
        assertThat(dto.getTitle()).isEqualTo("note.txt");
        assertThat(dto.getChunkCount()).isEqualTo(3);
        assertThat(dto.getMetadata()).containsEntry(MetadataKeys.FILE_NAME, "note.txt");
    }

    @Test
    void deletesDocumentViaDocumentStore() {
        DocumentStorePort documentStore = mock(DocumentStorePort.class);
        IngestionController deleteController = new IngestionController(service, webPageFetcher, null, documentStore);
        ResponseEntity<Void> res = deleteController.deleteDocument("d1");
        assertThat(res.getStatusCode()).isEqualTo(NO_CONTENT);
        verify(documentStore).delete("d1");
    }

    @Test
    void deleteReturnsUnavailableWhenDocumentStoreMissing() {
        ResponseEntity<Void> res = controller.deleteDocument("d1");
        assertThat(res.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void listDocumentsReturnsUnavailableWhenDocumentStoreMissing() {
        ResponseEntity<List<DocumentSummaryDTO>> res = controller.listDocuments();
        assertThat(res.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void ingestFileAsyncReturnsUnavailableWhenAsyncMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[]{1});
        ResponseEntity<IngestJobResponse> res = controller.ingestFileAsync(file, null);
        assertThat(res.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        verify(service, never()).ingest(any());
    }

    @Test
    void ingestStatusReturnsUnavailableWhenAsyncMissing() {
        ResponseEntity<IngestStatusDTO> res = controller.ingestStatus("d1");
        assertThat(res.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
    }

    @Test
    void ingestStatusReturnsNotFoundForUnknownJob() {
        AsyncIngestionService async = mock(AsyncIngestionService.class);
        when(async.status("gone")).thenReturn(new AsyncIngestionService.JobStatus(
                "gone", AsyncIngestionService.STATE_FAILED, 0, "No such ingestion job"));
        IngestionController asyncController = new IngestionController(service, webPageFetcher, async, null);
        ResponseEntity<IngestStatusDTO> res = asyncController.ingestStatus("gone");
        assertThat(res.getStatusCode()).isEqualTo(NOT_FOUND);
    }

    @Test
    void listsDocumentsWithoutTitleWhenMetadataLacksTitle() {
        DocumentStorePort documentStore = mock(DocumentStorePort.class);
        when(documentStore.listDocuments()).thenReturn(List.of(
                new DocumentSummary("d1", 2, Map.of(MetadataKeys.FILE_NAME, "note.txt"))));
        IngestionController listController = new IngestionController(service, webPageFetcher, null, documentStore);
        ResponseEntity<List<DocumentSummaryDTO>> res = listController.listDocuments();
        Assertions.assertNotNull(res.getBody());
        assertThat(res.getBody().getFirst().getTitle()).isNull();
        assertThat(res.getBody().getFirst().getDocumentId()).isEqualTo("d1");
    }

    @Test
    void mapsEmptyExtractionToUnprocessableEntity() {
        var exception = new IngestionService.EmptyExtractionException(new Document("d1", "", Map.of()));
        ResponseEntity<String> res = controller.handleEmptyExtraction(exception);
        assertThat(res.getStatusCode()).isEqualTo(UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("d1");
    }
}