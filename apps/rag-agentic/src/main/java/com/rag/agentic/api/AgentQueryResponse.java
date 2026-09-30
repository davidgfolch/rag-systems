package com.rag.agentic.api;

import com.rag.agentic.domain.AgentStep;
import com.rag.contract.model.ChunkResult;

import java.util.List;

/**
 * Full agentic query response: the generated answer, the sources that grounded
 * it and the observable multi-step trace (planning, tool calls, reflection).
 */
public record AgentQueryResponse(String answer, List<ChunkResult> sources, List<AgentStep> steps) {

    public AgentQueryResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}