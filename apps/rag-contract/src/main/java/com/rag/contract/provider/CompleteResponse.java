package com.rag.contract.provider;

/**
 * Non-streaming chat completion response.
 */
public record CompleteResponse(String answer) {
}