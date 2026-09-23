package com.rag.agentic.orchestration;

import com.rag.agentic.domain.AgentStep;
import com.rag.agentic.domain.AgentTrace;
import com.rag.common.core.domain.Chunk;
import com.rag.common.generation.StreamingChatModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Public entry point for agentic chat. {@code ask} runs the loop through to a
 * final answer; {@code askStream} runs the same loop to gather context and then
 * streams the answer tokens so the TUI can cancel an in-flight reply.
 */
public class AgenticChatService {

    private static final Logger log = LoggerFactory.getLogger(AgenticChatService.class);

    private final AgentLoop loop;
    private final StreamingChatModelPort streamingModel;

    public AgenticChatService(AgentLoop loop, StreamingChatModelPort streamingModel) {
        this.loop = loop;
        this.streamingModel = streamingModel;
    }

    public AgentQueryResult ask(String question, int topK) {
        AgentTrace trace = loop.run(question, topK);
        log.info("Agentic ask complete: steps={}, sources={}, answerLength={}",
                trace.steps().size(), trace.sources().size(), trace.answer().length());
        return new AgentQueryResult(trace.answer(), trace.sources(), trace.steps());
    }

    public Flux<String> askStream(String question, int topK) {
        AgentTrace trace = loop.collect(question, topK);
        var prompt = AgentLoop.ANSWER_PROMPT.formatted(trace.context(), question);
        log.info("Agentic stream starting: steps={}, sources={}, promptLength={}",
                trace.steps().size(), trace.sources().size(), prompt.length());
        return streamingModel.completeStream(prompt);
    }

    public record AgentQueryResult(String answer, List<Chunk> sources, List<AgentStep> steps) {
    }
}