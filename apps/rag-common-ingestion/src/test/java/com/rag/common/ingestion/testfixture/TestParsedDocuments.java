package com.rag.common.ingestion.testfixture;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;

import java.util.List;
import java.util.Map;

/** Shared parsed-document fixtures for ingestion tests. */
public final class TestParsedDocuments {

    private TestParsedDocuments() {
    }

    public static ParsedDocument text(String documentId, String text) {
        return new ParsedDocument(documentId, SourceType.TEXT, Map.of(),
                List.of(new DocumentBlock(BlockType.PARAGRAPH, text)));
    }
}
