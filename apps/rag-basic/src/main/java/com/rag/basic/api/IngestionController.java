package com.rag.basic.api;

import com.rag.common.domain.Document;
import com.rag.common.services.IngestionService;
import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.PageDTO;
import com.rag.basic.services.WebCrawlerClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for ingesting documents (raw content, multipart file or via rag-webcrawler).
 */
@RestController
@RequestMapping("/api/documents")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionService ingestionService;
    private final WebCrawlerClient webCrawlerClient;

    public IngestionController(IngestionService ingestionService, WebCrawlerClient webCrawlerClient) {
        this.ingestionService = ingestionService;
        this.webCrawlerClient = webCrawlerClient;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        if (request.getContent().isBlank() && !hasRawBytes(request)) {
            return ResponseEntity.badRequest().build();
        }
        var document = new Document(UUID.randomUUID().toString(), request.getContent(), request.getMetadata());
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
        metadata.putIfAbsent("sourceType", "file");
        metadata.putIfAbsent("fileName", file.getOriginalFilename());
        metadata.put("rawBytes", bytes);
        var document = new Document(UUID.randomUUID().toString(), "", metadata);
        log.info("Ingest-file '{}' -> document {}: parsing content...", original, document.getId());
        IngestionService.IngestionResult result = ingestionService.ingest(document);
        log.info("Ingest-file '{}' -> document {}: complete ({} chunks).",
                original, result.documentId(), result.chunkCount());
        return created(result);
    }

    private boolean hasRawBytes(IngestRequest request) {
        return request.getMetadata() != null && request.getMetadata().get("raw") != null;
    }

    @PostMapping("/ingest-url")
    public ResponseEntity<IngestResponse> ingestUrl(@RequestBody IngestUrlRequest request) {
        if (request.getUrl().toString().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        PageDTO page = webCrawlerClient.fetch(request.getUrl().toString());
        var document = new Document(UUID.randomUUID().toString(), page.getText(),
                Map.of("sourceType", "web", "source", page.getUrl()));
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