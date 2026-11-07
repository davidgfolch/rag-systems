package com.rag.common.app.pipeline;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.ChunkingConfig;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.ingestion.chunking.FixedSizeChunker;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import com.rag.common.ingestion.chunking.SemanticChunker;
import com.rag.common.ingestion.chunking.TokenChunker;
import com.rag.common.ingestion.chunking.structural.FormatAwareChunkerDispatcher;
import com.rag.contract.pipeline.ChunkingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkersTest {

    private final EmbeddingModelPort embeddings = text -> new float[]{1f, 0f};

    @Test
    void buildsTheChunkerForEachStrategy() {
        assertThat(Chunkers.of(ChunkingStrategy.FIXED, 512, 64, () -> embeddings))
                .isInstanceOf(FixedSizeChunker.class);
        assertThat(Chunkers.of(ChunkingStrategy.TOKEN, 512, 64, () -> embeddings))
                .isInstanceOf(TokenChunker.class);
        assertThat(Chunkers.of(ChunkingStrategy.RECURSIVE, 512, 64, () -> embeddings))
                .isInstanceOf(RecursiveCharacterChunker.class);
        assertThat(Chunkers.of(ChunkingStrategy.SEMANTIC, 512, 64, () -> embeddings))
                .isInstanceOf(SemanticChunker.class);
    }

    @Test
    void buildsAFormatAwareChunkerThatSupportsEveryParsedFormat() {
        var sut = Chunkers.chunker(new RecursiveCharacterChunker(512, 64), 512);
        assertThat(sut).isInstanceOf(FormatAwareChunkerDispatcher.class);
        for (var type : List.of(SourceType.TEXT, SourceType.UNKNOWN, SourceType.MARKDOWN,
                SourceType.HTML, SourceType.PDF, SourceType.DOCX, SourceType.JSON)) {
            assertThat(sut.supports(new ParsedDocument("d1", type, Map.of(), List.of()))).isTrue();
        }
    }

    @Test
    void formatAwareChunkerReusesTheVariantSplitterForPlainText() {
        var sut = Chunkers.chunker(new RecursiveCharacterChunker(512, 0), 512);
        var document = new ParsedDocument("d1", SourceType.TEXT, Map.of(),
                List.of(new DocumentBlock(BlockType.PARAGRAPH, "plain text")));
        var chunks = sut.chunk(document, ChunkingConfig.DEFAULT);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().getContent()).isEqualTo("plain text");
    }
}
