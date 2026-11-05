package com.rag.common.app.pipeline;

import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.ingestion.chunking.FixedSizeChunker;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import com.rag.common.ingestion.chunking.SemanticChunker;
import com.rag.common.ingestion.chunking.TokenChunker;
import com.rag.contract.pipeline.ChunkingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkersTest {

    private final EmbeddingModelPort embeddings = text -> List.of(1f, 0f);

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
}
