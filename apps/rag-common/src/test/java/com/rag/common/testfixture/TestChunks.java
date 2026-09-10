package com.rag.common.testfixture;

import com.rag.common.domain.Chunk;

import java.util.List;
import java.util.Map;

public final class TestChunks {

    public static final String ID = "chunk-001";
    public static final String DOCUMENT_ID = "doc-001";
    public static final int INDEX = 0;
    public static final String TEXT = "Hello world content for testing chunking behavior";
    public static final String SOURCE = "test.pdf";

    private TestChunks() {
    }

    public static Chunk small() {
        return new Chunk("chunk-small", DOCUMENT_ID, "Small text", 0, Map.of());
    }

    public static Chunk defaultChunk() {
        return new Chunk(ID, DOCUMENT_ID, TEXT, INDEX, Map.of());
    }

    public static Chunk large() {
        return new Chunk("chunk-large", DOCUMENT_ID, "A".repeat(500), 0, Map.of());
    }

    public static Chunk of(String text) {
        return new Chunk("chunk-of", DOCUMENT_ID, text, 0, Map.of());
    }

    public static Chunk withOffset(String text, int offset) {
        return new Chunk("chunk-off-" + offset, DOCUMENT_ID, text, offset, Map.of());
    }

    public static Chunk withScore(String text, double score) {
        return new Chunk("chunk-score", DOCUMENT_ID, text, 0, Map.of("score", score));
    }

    public static Chunk plain(String text) {
        return new Chunk("chunk-plain", DOCUMENT_ID, text, 0, Map.of());
    }

    public static Chunk withIdAndContent(String id, String content) {
        return new Chunk(id, DOCUMENT_ID, content, 0, Map.of());
    }

    public static List<Chunk> list(String... texts) {
        return java.util.stream.IntStream.range(0, texts.length)
            .mapToObj(i -> new Chunk("chunk-" + i, DOCUMENT_ID, texts[i], i, Map.of()))
            .toList();
    }

    public static List<Chunk> many(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> new Chunk("chunk-" + i, DOCUMENT_ID, "chunk-" + i, i, Map.of()))
            .toList();
    }
}
