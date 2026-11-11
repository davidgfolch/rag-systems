package com.rag.basic.api;

import com.rag.basic.services.RetrievalService;
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
    public QueryResponse query(@RequestBody QueryRequest req) {
        int topK = req.getTopK() == null ? 5 : req.getTopK();
        String documentId = req.getDocumentId();
        log.info("Query: topK={}, documentId={}", topK, documentId);
        List<Chunk> chunks = documentId == null
                ? retrievalService.retrieve(req.getQuestion(), topK)
                : retrievalService.retrieve(req.getQuestion(), topK, documentId);
        var results = chunks.stream()
                .map(QueryController::toResult)
                .toList();
        log.info("Query returned {} results", results.size());
        return new QueryResponse(req.getQuestion()).results(results);
    }

    private static ChunkResult toResult(Chunk chunk) {
        return new ChunkResult().id(chunk.getId()).documentId(chunk.getDocumentId())
                .content(chunk.getContent()).index(chunk.getIndex())
                .metadata(chunk.getMetadata());
    }
}