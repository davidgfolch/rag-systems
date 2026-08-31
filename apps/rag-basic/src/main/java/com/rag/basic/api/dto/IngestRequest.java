package com.rag.basic.api.dto;

import java.util.Map;

/**
 * Request DTO for document ingestion.
 */
public record IngestRequest(String content, Map<String, Object> metadata) {}
