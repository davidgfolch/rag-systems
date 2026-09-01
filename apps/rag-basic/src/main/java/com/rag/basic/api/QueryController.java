package com.rag.basic.api;

import com.rag.basic.services.RetrievalService;
import com.rag.common.domain.Chunk;
import com.rag.contract.model.ChunkResult;
import com.rag.contract.model.QueryRequest;
import com.rag.contract.model.QueryResponse;
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

    private final RetrievalService retrievalService;

    public QueryController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping
    public QueryResponse query(@RequestBody QueryRequest request) {
        int topK = request.getTopK() == null ? 5 : request.getTopK();
        String documentId = request.getDocumentId();
        List<Chunk> chunks = documentId == null
                ? retrievalService.retrieve(request.getQuestion(), topK)
                : retrievalService.retrieve(request.getQuestion(), topK, documentId);
        List<ChunkResult> results = chunks.stream()
                .map(QueryController::toResult)
                .toList();
        return new QueryResponse(request.getQuestion()).results(results);
    }

    private static ChunkResult toResult(Chunk chunk) {
        return new ChunkResult().id(chunk.getId()).documentId(chunk.getDocumentId())
                .content(chunk.getContent()).index(chunk.getIndex());
    }
}