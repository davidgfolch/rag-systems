package com.rag.agentic.api;

/**
 * Module-specific full agentic query: returns the generated answer, the traced
 * step list and the sources that grounded it. The shared contract only exposes
 * retrieval DTOs, so the richer shape lives here in the module.
 */
public record AgentQueryRequest(String question, Integer topK) {
}