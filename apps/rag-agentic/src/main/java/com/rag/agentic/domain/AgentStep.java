package com.rag.agentic.domain;

/**
 * One entry in the agent's execution trace, rendered back to the caller so the
 * multi-step behaviour (planning, tool calls, reflection) is observable.
 */
public record AgentStep(String label, String detail) {
}