package com.rag.common.testfixture;

import com.rag.common.domain.Document;

import java.util.List;
import java.util.Map;

import static com.rag.common.domain.MetadataKeys.RAW_BYTES;
import static com.rag.common.domain.MetadataKeys.SOURCE;

public final class TestDocuments {

    public static final String ID = "doc-001";
    public static final String SOURCE_NAME = "test.pdf";
    public static final String CONTENT = "Hello world content for testing";

    private TestDocuments() {
    }

    public static Document empty() {
        return new Document(ID, "", Map.of());
    }

    public static Document defaultDoc() {
        return new Document(ID, CONTENT, Map.of(SOURCE, SOURCE_NAME));
    }

    public static Document withContent(String content) {
        return new Document(ID, content, Map.of(SOURCE, SOURCE_NAME));
    }

    public static Document withId(String id) {
        return new Document(id, CONTENT, Map.of(SOURCE, SOURCE_NAME));
    }

    public static Document withRawBytes(byte[] bytes) {
        return new Document(ID, CONTENT, Map.of(SOURCE, SOURCE_NAME, RAW_BYTES, bytes));
    }

    public static Document withSource(String source) {
        return new Document(ID, CONTENT, Map.of(SOURCE, source));
    }

    public static Document withIdContent(String id, String content) {
        return new Document(id, content, Map.of());
    }

    public static List<Document> list(String... sources) {
        return java.util.stream.IntStream.range(0, sources.length)
            .mapToObj(i -> new Document("doc-" + i, "content-" + i, Map.of(SOURCE, sources[i])))
            .toList();
    }

    public static List<Document> many(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> new Document("doc-" + i, "content-" + i, Map.of(SOURCE, "file-" + i + ".pdf")))
            .toList();
    }
}
