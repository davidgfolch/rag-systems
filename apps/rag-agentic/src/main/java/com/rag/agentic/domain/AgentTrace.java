package com.rag.agentic.domain;

import com.rag.common.core.domain.Chunk;

import java.util.List;

/**
 * Full result of one agent run: the generated answer (null when the loop only
 * gathered context for streaming), the accumulated vector sources, the
 * observable step trace and the condensed context text fed to the final answer
 * prompt.
 */
public record AgentTrace(String answer, List<Chunk> sources, List<AgentStep> steps, String context) {

    public AgentTrace {
        sources = List.copyOf(sources);
        steps = List.copyOf(steps);
        context = context == null ? "" : context;
    }

    public AgentTrace(String answer, List<Chunk> sources, List<AgentStep> steps) {
        this(answer, sources, steps, "");
    }

    public boolean hasSources() {
        return !sources.isEmpty();
    }
}