package com.rag.contract.provider;

/**
 * Non-streaming chat completion request.
 */
public record CompleteRequest(String prompt) {
}