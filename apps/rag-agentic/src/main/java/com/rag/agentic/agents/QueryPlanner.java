package com.rag.agentic.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Query planning: deterministic decomposition of a compound question into
 * concrete sub-queries. The agent then retrieves one sub-query per step, which
 * is what makes multi-hop retrieval bounded and observable. A single question
 * yields a single-element plan.
 */
public class QueryPlanner {

    private static final Logger log = LoggerFactory.getLogger(QueryPlanner.class);

    private static final List<String> CONNECTORS = List.of(" and ", " then ", " but ", "; ", ";", "?", ".");

    public List<String> plan(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String part : CONNECTORS.stream()
                .reduce(List.of(question), QueryPlanner::splitAll, (a, b) -> a)) {
            var trimmed = part == null ? "" : part.trim();
            if (!trimmed.isBlank()) {
                seen.add(trimmed);
            }
        }
        var plan = new ArrayList<>(seen);
        log.debug("Query plan for '{}': {} sub-queries", question, plan.size());
        return List.copyOf(plan);
    }

    private static List<String> splitAll(List<String> parts, String connector) {
        var out = new ArrayList<String>();
        for (String part : parts) {
            out.addAll(List.of(part.split(Pattern.quote(connector))));
        }
        return out;
    }
}