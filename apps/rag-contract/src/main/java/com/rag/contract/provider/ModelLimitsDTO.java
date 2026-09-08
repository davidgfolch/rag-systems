package com.rag.contract.provider;

/**
 * Token limits of a catalog model.
 *
 * @param context context window size in tokens
 * @param output  maximum output tokens (0 when not published)
 */
public record ModelLimitsDTO(long context, long output) {
}