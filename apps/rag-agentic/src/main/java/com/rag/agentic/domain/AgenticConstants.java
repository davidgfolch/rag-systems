package com.rag.agentic.domain;

/**
 * Shared constants for the agent loop: tool names, JSON field names and the
 * default search widening. Imported by tests instead of duplicating literals.
 */
public final class AgenticConstants {

    public static final String TOOL_SEARCH = "search";
    public static final String TOOL_DOCUMENT_SEARCH = "document_search";
    public static final String TOOL_WEB_SEARCH = "web_search";

    public static final String ACTION_OVERALL = "action";
    public static final String ACTION_TOOL = "tool";
    public static final String ACTION_ANSWER = "answer";
    public static final String FIELD_ARGS = "args";
    public static final String FIELD_QUERY = "query";
    public static final String FIELD_DOCUMENT_ID = "documentId";
    public static final String FIELD_URL = "url";
    public static final String FIELD_TOP_K = "topK";

    public static final int DEFAULT_TOP_K = 5;
    public static final int DEFAULT_MAX_STEPS = 3;
    public static final int UNSPECIFIED_TOP_K = 0;

    private AgenticConstants() {
    }
}