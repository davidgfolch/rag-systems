package com.rag.basic.api;

import com.rag.common.domain.Document;
import com.rag.common.services.IngestionService;
import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.Page;
import com.rag.basic.services.WebCrawlerClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for ingesting documents (raw content or via rag-webcrawler).
 */
@RestController
@RequestMapping("/api/documents")
public class IngestionController {

    private final IngestionService ingestionService;
    private final WebCrawlerClient webCrawlerClient;

    public IngestionController(IngestionService ingestionService, WebCrawlerClient webCrawlerClient) {
        this.ingestionService = ingestionService;
        this.webCrawlerClient = webCrawlerClient;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        if (request.getContent().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var document = new Document(UUID.randomUUID().toString(), request.getContent(), request.getMetadata());
        return created(ingestionService.ingest(document));
    }

    @PostMapping("/ingest-url")
    public ResponseEntity<IngestResponse> ingestUrl(@RequestBody IngestUrlRequest request) {
        if (request.getUrl().toString().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Page page = webCrawlerClient.fetch(request.getUrl().toString());
        var document = new Document(UUID.randomUUID().toString(), page.getText(),
                Map.of("sourceType", "web", "source", page.getUrl()));
        return created(ingestionService.ingest(document));
    }

    private ResponseEntity<IngestResponse> created(IngestionService.IngestionResult result) {
        var response = new IngestResponse().documentId(result.documentId()).chunkCount(result.chunkCount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}