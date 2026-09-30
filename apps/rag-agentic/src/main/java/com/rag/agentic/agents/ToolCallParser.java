package com.rag.agentic.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.agentic.domain.AgenticConstants;
import com.rag.agentic.domain.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.rag.agentic.domain.AgenticConstants.ACTION_ANSWER;
import static com.rag.agentic.domain.AgenticConstants.ACTION_OVERALL;
import static com.rag.agentic.domain.AgenticConstants.ACTION_TOOL;
import static com.rag.agentic.domain.AgenticConstants.FIELD_ARGS;
import static com.rag.agentic.domain.AgenticConstants.FIELD_DOCUMENT_ID;
import static com.rag.agentic.domain.AgenticConstants.FIELD_QUERY;
import static com.rag.agentic.domain.AgenticConstants.FIELD_TOP_K;
import static com.rag.agentic.domain.AgenticConstants.FIELD_URL;

/**
 * Parses the model's tool-selection output into {@link ToolCall}s. The model is
 * asked for JSON of the form {@code {"action":"tool","tool":"<name>","args":{...}}}
 * but prose may surround the JSON, so extraction scans for balanced brace
 * blocks and tolerates both a single object and a top-level array. No
 * parseable call yields an empty list, which the loop treats as "use the
 * deterministic fallback".
 */
public class ToolCallParser {

    private static final Logger log = LoggerFactory.getLogger(ToolCallParser.class);

    private final ObjectMapper objectMapper;

    public ToolCallParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ToolCall> parse(String modelOutput) {
        if (modelOutput == null || modelOutput.isBlank()) {
            return List.of();
        }
        var calls = braceBlocks(modelOutput).stream()
                .map(this::toCall)
                .flatMap(Optional::stream)
                .toList();
        log.debug("Parsed {} tool calls from model output", calls.size());
        return calls;
    }

    private Optional<ToolCall> toCall(String block) {
        try {
            var node = objectMapper.readTree(block);
            if (!node.isObject() || ACTION_ANSWER.equals(node.path(ACTION_OVERALL).asText(""))) {
                return Optional.empty();
            }
            String name = text(node.path(ACTION_TOOL).asText(""), node.path("name").asText(""));
            if (name.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(toolCall(node, name));
        } catch (Exception exception) {
            log.debug("Skipping unparseable tool-call block: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private ToolCall toolCall(JsonNode node, String name) {
        var args = args(node);
        return new ToolCall(name,
                asString(args.get(FIELD_QUERY), node.path(FIELD_QUERY).asText("")),
                asString(args.get(FIELD_DOCUMENT_ID), node.path(FIELD_DOCUMENT_ID).asText("")),
                asString(args.get(FIELD_URL), node.path(FIELD_URL).asText("")),
                asInt(args.get(FIELD_TOP_K), node.path(FIELD_TOP_K).asInt(AgenticConstants.UNSPECIFIED_TOP_K)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> args(JsonNode node) {
        return node.has(FIELD_ARGS) && node.path(FIELD_ARGS).isObject()
                ? objectMapper.convertValue(node.path(FIELD_ARGS), Map.class)
                : Map.of();
    }

    private static String text(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private static List<String> braceBlocks(String text) {
        var quoted = stringMask(text);
        var blocks = new ArrayList<String>();
        int start = -1;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted[i]) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    blocks.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return blocks;
    }

    private static boolean[] stringMask(String text) {
        var quoted = new boolean[text.length()];
        boolean inString = false;
        char quote = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (inString) {
                quoted[i] = true;
                if (c == '\\') {
                    if (i + 1 < text.length()) {
                        quoted[i + 1] = true;
                        i++;
                    }
                } else if (c == quote) {
                    inString = false;
                }
            } else if (c == '"' || c == '\'') {
                quoted[i] = true;
                inString = true;
                quote = c;
            }
            i++;
        }
        return quoted;
    }
}