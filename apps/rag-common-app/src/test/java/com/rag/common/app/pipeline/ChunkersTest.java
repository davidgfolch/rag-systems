package com.rag.common.app.pipeline;

import com.rag.common.ingestion.chunking.FixedSizeChunker;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import com.rag.common.ingestion.chunking.TokenChunker;
import com.rag.contract.pipeline.ChunkingStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkersTest {

    @Test
    void buildsTheChunkerForEachStrategy() {
        assertThat(Chunkers.of(ChunkingStrategy.FIXED, 512, 64)).isInstanceOf(FixedSizeChunker.class);
        assertThat(Chunkers.of(ChunkingStrategy.TOKEN, 512, 64)).isInstanceOf(TokenChunker.class);
        assertThat(Chunkers.of(ChunkingStrategy.RECURSIVE, 512, 64)).isInstanceOf(RecursiveCharacterChunker.class);
        assertThat(Chunkers.of(ChunkingStrategy.SEMANTIC, 512, 64)).isInstanceOf(RecursiveCharacterChunker.class);
    }
}
