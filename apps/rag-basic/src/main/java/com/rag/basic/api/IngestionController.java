package com.rag.basic.api;

import com.rag.basic.api.dto.IngestRequest;
import com.rag.basic.api.dto.IngestResponse;
import com.rag.basic.services.IngestionService;
import com.rag.common.domain.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoint for ingesting documents into the vector store.
 */
@RestController
@RequestMapping("/api/documents")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Document document = new Document(UUID.randomUUID().toString(), request.content(), request.metadata());
        IngestionService.IngestionResult result = ingestionService.ingest(document);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IngestResponse(result.documentId(), result.chunkCount()));
    }
}