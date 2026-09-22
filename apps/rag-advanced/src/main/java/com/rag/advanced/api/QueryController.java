package com.rag.advanced.api;

import com.rag.advanced.services.AdvancedRetrievalService;
import com.rag.common.core.domain.Chunk;
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
import java.util.Map;

/**
 * Retrieval endpoint. The standard {@code /api/query} honors the shared
 * contract; {@code /api/query/advanced} adds a metadata filter and the hybrid
 * vector + lexical re-ranking pass.
 */
@RestController
@RequestMapping("/api/query")
public class QueryController {

    private static final Logger log = LoggerFactory.getLogger(QueryController.class);
    private static final int DEFAULT_TOP_K = 5;

    private final AdvancedRetrievalService retrievalService;

    public QueryController(AdvancedRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping
    public QueryResponse query(@RequestBody QueryRequest request) {
        int topK = request.getTopK() != null ? request.getTopK() : DEFAULT_TOP_K;
        log.info("Query: topK={}, documentId={}", topK, request.getDocumentId());
        return response(request.getQuestion(), retrieve(request.getQuestion(), topK, request.getDocumentId(), Map.of()));
    }

    @PostMapping("/advanced")
    public QueryResponse advancedQuery(@RequestBody AdvancedQueryRequest request) {
        int topK = request.topK() != null ? request.topK() : DEFAULT_TOP_K;
        log.info("Advanced query: topK={}, documentId={}, metadata={}", topK, request.documentId(), request.metadata());
        return response(request.question(), retrieve(request.question(), topK, request.documentId(), request.metadata()));
    }

    private List<Chunk> retrieve(String question, int topK, String documentId, Map<String, Object> metadata) {
        return documentId != null && !documentId.isBlank()
                ? retrievalService.retrieve(question, topK, documentId, metadata)
                : retrievalService.retrieve(question, topK, metadata);
    }

    private static QueryResponse response(String question, List<Chunk> chunks) {
        var results = chunks.stream().map(QueryController::chunkResult).toList();
        log.info("Query response contains {} chunks", results.size());
        return new QueryResponse(question).results(results);
    }

    private static ChunkResult chunkResult(Chunk chunk) {
        return new ChunkResult().id(chunk.getId()).documentId(chunk.getDocumentId())
                .content(chunk.getContent()).index(chunk.getIndex());
    }
}