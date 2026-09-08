package com.rag.contract.provider;

/**
 * Capability flags of a catalog model.
 *
 * @param reasoning        supports chain-of-thought reasoning
 * @param toolCall         supports tool/function calling
 * @param structuredOutput supports structured (JSON) output
 */
public record ModelCapabilitiesDTO(boolean reasoning, boolean toolCall, boolean structuredOutput) {
}