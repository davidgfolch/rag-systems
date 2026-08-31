package com.rag.basic.api;

import com.rag.basic.api.dto.QueryResponse;
import com.rag.basic.services.RetrievalService;
import com.rag.common.domain.Chunk;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public QueryResponse query(@RequestParam String q,
                               @RequestParam(defaultValue = "5") int topK) {
        List<Chunk> chunks = retrievalService.retrieve(q, topK);
        List<QueryResponse.ChunkResult> results = chunks.stream()
                .map(QueryResponse.ChunkResult::from)
                .toList();
        return new QueryResponse(q, results);
    }
}