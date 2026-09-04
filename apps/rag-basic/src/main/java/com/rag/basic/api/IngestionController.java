package com.rag.basic.api;

import com.rag.common.domain.Document;
import com.rag.common.domain.DocumentSummary;
import com.rag.common.services.IngestionService;
import com.rag.common.services.AsyncIngestionService;
import com.rag.basic.services.RetrievalService;
import com.rag.basic.services.WebCrawlerClient;
import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestStatusDTO;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.contract.model.PageDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for ingesting documents (raw content, multipart file or via rag-webcrawler).
 */
@RestController
@RequestMapping("/api/documents")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);
    private static final String SOURCE_TYPE = "sourceType";
    private static final String TITLE = "title";

    private final IngestionService ingestionService;
    private final WebCrawlerClient webCrawlerClient;
    private final AsyncIngestionService asyncIngestionService;
    private final RetrievalService retrievalService;

    public IngestionController(IngestionService ingestionService, WebCrawlerClient webCrawlerClient) {
        this(ingestionService, webCrawlerClient, null, null);
    }

    @Autowired
    public IngestionController(IngestionService ingestionService, WebCrawlerClient webCrawlerClient,
                               AsyncIngestionService asyncIngestionService, RetrievalService retrievalService) {
        this.ingestionService = ingestionService;
        this.webCrawlerClient = webCrawlerClient;
        this.asyncIngestionService = asyncIngestionService;
        this.retrievalService = retrievalService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentSummaryDTO>> listDocuments() {
        if (retrievalService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        List<DocumentSummaryDTO> documents = retrievalService.listDocuments().stream()
                .map(this::toDocumentSummary)
                .toList();
        return ResponseEntity.ok(documents);
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        if (request.getContent().isBlank() && !hasRawBytes(request)) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> metadata = new HashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        metadata.putIfAbsent(TITLE, "Untitled document");
        var document = new Document(UUID.randomUUID().toString(), request.getContent(), metadata);
        return created(ingestionService.ingest(document));
    }

    @PostMapping(value = "/ingest-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestResponse> ingestFile(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "fileInfo", required = false) Map<String, Object> fileInfo) throws IOException {
        String original = file.getOriginalFilename();
        if (file.isEmpty()) {
            log.warn("Ingest-file rejected: '{}' is empty ({} bytes)", original, file.getSize());
            return ResponseEntity.badRequest().build();
        }
        byte[] bytes = file.getBytes();
        log.info("Ingest-file received: '{}' ({} bytes, type={}). Starting ingestion pipeline...",
                original, bytes.length, file.getContentType());
        Map<String, Object> metadata = new HashMap<>();
        if (fileInfo != null) {
            metadata.putAll(fileInfo);
        }
        metadata.putIfAbsent(SOURCE_TYPE, "file");
        metadata.putIfAbsent("fileName", file.getOriginalFilename());
        metadata.putIfAbsent(TITLE, file.getOriginalFilename());
        metadata.put("rawBytes", bytes);
        var document = new Document(UUID.randomUUID().toString(), "", metadata);
        log.info("Ingest-file '{}' -> document {}: parsing content...", original, document.getId());
        IngestionService.IngestionResult result = ingestionService.ingest(document);
        log.info("Ingest-file '{}' -> document {}: complete ({} chunks).",
                original, result.documentId(), result.chunkCount());
        return created(result);
    }

    @PostMapping(value = "/ingest-file-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestJobResponse> ingestFileAsync(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "fileInfo", required = false) Map<String, Object> fileInfo) throws IOException {
        String original = file.getOriginalFilename();
        if (asyncIngestionService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (file.isEmpty()) {
            log.warn("Ingest-file-async rejected: '{}' is empty ({} bytes)", original, file.getSize());
            return ResponseEntity.badRequest().build();
        }
        byte[] bytes = file.getBytes();
        Map<String, Object> metadata = new HashMap<>();
        if (fileInfo != null) {
            metadata.putAll(fileInfo);
        }
        metadata.putIfAbsent(SOURCE_TYPE, "file");
        metadata.putIfAbsent("fileName", file.getOriginalFilename());
        metadata.putIfAbsent(TITLE, file.getOriginalFilename());
        metadata.put("rawBytes", bytes);
        var document = new Document(UUID.randomUUID().toString(), "", metadata);
        String documentId = asyncIngestionService.submit(document);
        log.info("Ingest-file-async submitted: '{}' ({} bytes) -> document {}", original, bytes.length, documentId);
        return ResponseEntity.accepted()
                .body(new IngestJobResponse().documentId(documentId));
    }

    @GetMapping("/ingest-status/{documentId}")
    public ResponseEntity<IngestStatusDTO> ingestStatus(@PathVariable String documentId) {
        if (asyncIngestionService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        AsyncIngestionService.JobStatus status = asyncIngestionService.status(documentId);
        if (AsyncIngestionService.STATE_FAILED.equals(status.state())
                && "No such ingestion job".equals(status.message())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new IngestStatusDTO()
                .documentId(status.documentId())
                .state(IngestStatusDTO.StateEnum.fromValue(status.state()))
                .chunkCount(status.chunkCount())
                .message(status.message()));
    }

    private boolean hasRawBytes(IngestRequest request) {
        return request.getMetadata() != null && request.getMetadata().get("raw") != null;
    }

    private DocumentSummaryDTO toDocumentSummary(DocumentSummary summary) {
        Object title = summary.metadata().get(TITLE);
        return new DocumentSummaryDTO()
                .documentId(summary.documentId())
                .title(title != null ? title.toString() : null)
                .chunkCount(summary.chunkCount())
                .metadata(summary.metadata());
    }

    @PostMapping("/ingest-url")
    public ResponseEntity<IngestResponse> ingestUrl(@RequestBody IngestUrlRequest request) {
        if (request.getUrl().toString().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        PageDTO page = webCrawlerClient.fetch(request.getUrl().toString());
        var document = new Document(UUID.randomUUID().toString(), page.getText(),
                Map.of(SOURCE_TYPE, "web", "source", page.getUrl(), TITLE, page.getTitle()));
        return created(ingestionService.ingest(document));
    }

    private ResponseEntity<IngestResponse> created(IngestionService.IngestionResult result) {
        var response = new IngestResponse().documentId(result.documentId()).chunkCount(result.chunkCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IngestionService.EmptyExtractionException.class)
    public ResponseEntity<String> handleEmptyExtraction(IngestionService.EmptyExtractionException e) {
        return ResponseEntity.unprocessableEntity().body(e.getMessage());
    }
}