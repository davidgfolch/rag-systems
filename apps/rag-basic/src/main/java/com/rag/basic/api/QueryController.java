package com.rag.basic.api;

import com.rag.basic.services.RetrievalService;
import com.rag.common.domain.Chunk;
import com.rag.contract.model.ChunkResult;
import com.rag.contract.model.QueryRequest;
import com.rag.contract.model.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoint for similarity search over ingested documents.
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final RetrievalService retrievalService;

    public QueryController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping
    public QueryResponse query(@RequestBody QueryRequest request) {
        int topK = request.getTopK() == null ? 5 : request.getTopK();
        String documentId = request.getDocumentId();
        log.info("Query: topK={}, documentId={}", topK, documentId);
        List<Chunk> chunks = documentId == null
                ? retrievalService.retrieve(request.getQuestion(), topK)
                : retrievalService.retrieve(request.getQuestion(), topK, documentId);
        List<ChunkResult> results = chunks.stream()
                .map(QueryController::toResult)
                .toList();
        log.info("Query returned {} results", results.size());
        return new QueryResponse(request.getQuestion()).results(results);
    }

    private static ChunkResult toResult(Chunk chunk) {
        return new ChunkResult().id(chunk.getId()).documentId(chunk.getDocumentId())
                .content(chunk.getContent()).index(chunk.getIndex());
    }
}