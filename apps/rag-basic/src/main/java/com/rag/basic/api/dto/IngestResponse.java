package com.rag.basic.api.dto;

/**
 * Response DTO for a successful ingestion.
 */
public record IngestResponse(String documentId, int chunkCount) {}
