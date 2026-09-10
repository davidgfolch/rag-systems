package com.rag.tui.testfixture;

import com.rag.contract.model.DocumentSummaryDTO;

import java.time.OffsetDateTime;
import java.util.List;

public final class TestDocumentSummaries {

    public static final String DOC_ID = "doc-001";
    public static final String TITLE = "note.txt";
    public static final int CHUNK_COUNT = 3;

    private TestDocumentSummaries() {
    }

    public static DocumentSummaryDTO defaultSummary() {
        return new DocumentSummaryDTO().documentId(DOC_ID).title(TITLE).chunkCount(CHUNK_COUNT)
                .createdAt(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
    }

    public static DocumentSummaryDTO withId(String id) {
        return new DocumentSummaryDTO().documentId(id).title(TITLE).chunkCount(CHUNK_COUNT);
    }

    public static DocumentSummaryDTO withTitle(String title) {
        return new DocumentSummaryDTO().documentId(DOC_ID).title(title).chunkCount(CHUNK_COUNT);
    }

    public static DocumentSummaryDTO withChunks(int chunks) {
        return new DocumentSummaryDTO().documentId(DOC_ID).title(TITLE).chunkCount(chunks);
    }

    public static List<DocumentSummaryDTO> list(String... titles) {
        return java.util.stream.IntStream.range(0, titles.length)
            .mapToObj(i -> new DocumentSummaryDTO().documentId("doc-" + i).title(titles[i]).chunkCount(CHUNK_COUNT))
            .toList();
    }

    public static List<DocumentSummaryDTO> many(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> new DocumentSummaryDTO().documentId("doc-" + i).title("file-" + i + ".pdf").chunkCount(CHUNK_COUNT))
            .toList();
    }
}