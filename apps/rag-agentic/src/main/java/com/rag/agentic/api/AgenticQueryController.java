package com.rag.agentic.api;

import com.rag.agentic.orchestration.AgenticChatService;
import com.rag.agentic.services.AgenticRetrievalService;
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

/**
 * Retrieval endpoints. The standard {@code /api/query} honors the shared
 * contract and runs the agent loop in collect mode; {@code /api/query/agentic}
 * exposes the full answer, the step trace and the gathered sources.
 */
@RestController
@RequestMapping("/api/query")
public class AgenticQueryController {

    private static final Logger log = LoggerFactory.getLogger(AgenticQueryController.class);
    private static final int DEFAULT_TOP_K = 5;

    private final AgenticRetrievalService retrievalService;
    private final AgenticChatService chatService;

    public AgenticQueryController(AgenticRetrievalService retrievalService, AgenticChatService chatService) {
        this.retrievalService = retrievalService;
        this.chatService = chatService;
    }

    @PostMapping
    public QueryResponse query(@RequestBody QueryRequest request) {
        int topK = request.getTopK() != null ? request.getTopK() : DEFAULT_TOP_K;
        log.info("Query: topK={}, questionLength={}", topK, request.getQuestion().length());
        var results = retrievalService.retrieve(request.getQuestion(), topK).stream()
                .map(this::chunkResult)
                .toList();
        return new QueryResponse(request.getQuestion()).results(results);
    }

    @PostMapping("/agentic")
    public AgentQueryResponse agenticQuery(@RequestBody AgentQueryRequest request) {
        int topK = request.topK() != null ? request.topK() : DEFAULT_TOP_K;
        log.info("Agentic query: topK={}, questionLength={}", topK, request.question().length());
        var result = chatService.ask(request.question(), topK);
        var sources = result.sources().stream().map(this::chunkResult).toList();
        return new AgentQueryResponse(result.answer(), sources, result.steps());
    }

    private ChunkResult chunkResult(Chunk chunk) {
        return new ChunkResult().id(chunk.getId()).documentId(chunk.getDocumentId())
                .content(chunk.getContent()).index(chunk.getIndex());
    }
}