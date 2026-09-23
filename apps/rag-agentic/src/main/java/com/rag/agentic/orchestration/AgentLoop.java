package com.rag.agentic.orchestration;

import com.rag.agentic.agents.QueryPlanner;
import com.rag.agentic.agents.ReflectionAgent;
import com.rag.agentic.agents.ToolCallParser;
import com.rag.agentic.domain.AgenticConstants;
import com.rag.agentic.domain.AgentStep;
import com.rag.agentic.domain.AgentTrace;
import com.rag.agentic.domain.ToolCall;
import com.rag.agentic.domain.ToolResult;
import com.rag.agentic.tools.AgentTool;
import com.rag.common.core.domain.Chunk;
import com.rag.common.core.services.ChatModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.rag.agentic.domain.AgenticConstants.TOOL_SEARCH;

/**
 * The agentic retrieval loop. It lets the model pick tools via JSON, falls back
 * to a deterministic search over each planned sub-query when the model yields
 * nothing usable, and stops as soon as the reflection agent deems the gathered
 * context sufficient (or the step budget is spent). Collecting context and
 * generating the final answer are separated so the streaming service can stream
 * the answer tokens while running the same loop.
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private static final String TOOL_PROMPT = """
            You are a retrieval agent. The user question was decomposed into sub-queries:
            %s

            Available tools:
            - %s {"args":{"query":"...","topK":3}}
            - %s {"args":{"query":"...","documentId":"..."}}
            - %s {"args":{"url":"..."}}

            Return a JSON array of up to %d tool calls that collect the most relevant
            context. Example:
            [{"action":"tool","tool":"search","args":{"query":"fast retrieval"}}]
            If no more context is needed, return [].
            Question: %s""";

    public static final String ANSWER_PROMPT = """
            You are a helpful assistant. Answer the question using ONLY the provided
            context. If the answer is not in the context, say you do not know.

            Context:
            %s

            Question: %s
            Answer:""";

    private static final int STEP_DETAIL_LIMIT = 160;
    private static final String SUB_QUERY_JOIN = "; ";

    private final ToolRegistry registry;
    private final QueryPlanner planner;
    private final ReflectionAgent reflection;
    private final ToolCallParser parser;
    private final ChatModelPort chatModel;
    private final int maxSteps;

    public AgentLoop(ToolRegistry registry, QueryPlanner planner, ReflectionAgent reflection,
                     ToolCallParser parser, ChatModelPort chatModel, int maxSteps) {
        this.registry = registry;
        this.planner = planner;
        this.reflection = reflection;
        this.parser = parser;
        this.chatModel = chatModel;
        this.maxSteps = maxSteps > 0 ? maxSteps : AgenticConstants.DEFAULT_MAX_STEPS;
    }

    public AgentTrace run(String question, int topK) {
        var trace = collect(question, topK);
        var answer = chatModel.complete(ANSWER_PROMPT.formatted(trace.context(), question));
        log.info("Agent run complete: sources={}, steps={}, answerLength={}",
                trace.sources().size(), trace.steps().size(), answer.length());
        return new AgentTrace(answer, trace.sources(), trace.steps(), trace.context());
    }

    public AgentTrace collect(String question, int topK) {
        var subQueries = planned(question);
        var sources = new ArrayList<Chunk>();
        var steps = new ArrayList<AgentStep>();
        var context = new StringBuilder();
        int used = 0;
        boolean llmConsulted = false;
        while (reflection.shouldContinue(!sources.isEmpty(), used, maxSteps)) {
            if (!llmConsulted) {
                llmConsulted = true;
                var calls = parser.parse(chatModel.complete(
                        TOOL_PROMPT.formatted(String.join(SUB_QUERY_JOIN, subQueries),
                                TOOL_SEARCH, AgenticConstants.TOOL_DOCUMENT_SEARCH,
                                AgenticConstants.TOOL_WEB_SEARCH, maxSteps, question)));
                if (!calls.isEmpty()) {
                    for (var call : calls) {
                        if (used >= maxSteps) {
                            break;
                        }
                        execute(registry.resolve(call), call, sources, steps, context);
                        used++;
                    }
                    continue;
                }
            }
            var subQuery = subQueries.get(Math.min(used, subQueries.size() - 1));
            execute(registry.fallback(), new ToolCall(TOOL_SEARCH, subQuery, "", "", topK), sources, steps, context);
            used++;
        }
        log.info("Agent gathered {} sources in {} steps", sources.size(), steps.size());
        return new AgentTrace(null, sources, steps, context.toString());
    }

    private void execute(AgentTool tool, ToolCall call, List<Chunk> sources,
                         List<AgentStep> steps, StringBuilder context) {
        ToolResult result = tool.execute(call);
        merge(sources, result.chunks());
        appendContext(context, result.text());
        steps.add(new AgentStep("tool:" + tool.name(), abbreviate(result.text())));
        if (log.isDebugEnabled()) {
            log.debug("Executed tool {} -> {} chars, {} chunks",
                    tool.name(), result.text().length(), result.chunks().size());
        }
    }

    private List<String> planned(String question) {
        var plan = planner.plan(question);
        return plan.isEmpty() ? List.of(String.valueOf(question)) : plan;
    }

    private static void merge(List<Chunk> sources, List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            if (sources.stream().noneMatch(s -> s.getId().equals(chunk.getId()))) {
                sources.add(chunk);
            }
        }
    }

    private static void appendContext(StringBuilder context, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!context.isEmpty()) {
            context.append("\n---\n");
        }
        context.append(text);
    }

    private static String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "(no results)";
        }
        return text.length() <= STEP_DETAIL_LIMIT ? text : text.substring(0, STEP_DETAIL_LIMIT) + "...";
    }
}