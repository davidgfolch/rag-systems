package com.rag.common.app.pipeline;

import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentChunker;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.core.services.TextSplitter;
import com.rag.common.ingestion.chunking.FixedSizeChunker;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import com.rag.common.ingestion.chunking.SemanticChunker;
import com.rag.common.ingestion.chunking.TokenChunker;
import com.rag.common.ingestion.chunking.structural.FormatAwareChunkerDispatcher;
import com.rag.common.ingestion.chunking.structural.JsonBlockGrouper;
import com.rag.common.ingestion.chunking.structural.SectionAwareBlockGrouper;
import com.rag.common.ingestion.chunking.structural.StructuralDocumentChunker;
import com.rag.contract.pipeline.ChunkingStrategy;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Builds the chunker that backs one pipeline variant. */
public final class Chunkers {

    public static final int CHARS_PER_TOKEN = 4;

    private Chunkers() {
    }

    /**
     * @param embeddings embedding source the semantic chunker cuts with; only
     *                   resolved for {@link ChunkingStrategy#SEMANTIC}, so the
     *                   other strategies never build an embedding client
     */
    public static TextSplitter of(ChunkingStrategy strategy, int size, int overlap,
                                  Supplier<EmbeddingModelPort> embeddings) {
        return switch (strategy) {
            case FIXED -> new FixedSizeChunker(size, overlap);
            case TOKEN -> new TokenChunker(size / CHARS_PER_TOKEN, overlap / CHARS_PER_TOKEN);
            case RECURSIVE -> new RecursiveCharacterChunker(size, overlap);
            case SEMANTIC -> new SemanticChunker(embeddings.get(), size, overlap);
        };
    }

    /**
     * Wraps a variant's splitter in a format-aware chunker, so every supported
     * format keeps its structure while reusing the variant's splitting window.
     */
    public static DocumentChunker chunker(TextSplitter splitter, int size) {
        var sections = new SectionAwareBlockGrouper();
        return new FormatAwareChunkerDispatcher(List.of(
                new StructuralDocumentChunker(Set.of(SourceType.TEXT, SourceType.UNKNOWN),
                        splitter, sections, size),
                new StructuralDocumentChunker(SourceType.MARKDOWN, splitter, sections, size),
                new StructuralDocumentChunker(SourceType.HTML, splitter, sections, size),
                new StructuralDocumentChunker(SourceType.PDF, splitter, sections, size),
                new StructuralDocumentChunker(SourceType.DOCX, splitter, sections, size),
                new StructuralDocumentChunker(SourceType.JSON, splitter, new JsonBlockGrouper(), size)));
    }

}
