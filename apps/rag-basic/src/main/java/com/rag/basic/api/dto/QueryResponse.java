package com.rag.basic.api.dto;

import com.rag.common.domain.Chunk;

import java.util.List;

/**
 * Response DTO wrapping a list of retrieved chunks.
 */
public record QueryResponse(String query, List<ChunkResult> results) {

    public record ChunkResult(String id, String documentId, String content, int index) {

        public static ChunkResult from(Chunk chunk) {
            return new ChunkResult(chunk.getId(), chunk.getDocumentId(), chunk.getContent(), chunk.getIndex());
        }
    }
}
