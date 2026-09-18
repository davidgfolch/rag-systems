package com.rag.common.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingModelPortTest {

    @Test
    void defaultBatchEmbedDelegatesToSingleEmbed() {
        EmbeddingModelPort port = text -> List.of((float) text.length());
        assertThat(port.embed(List.of("ab", "cde"))).containsExactly(List.of(2.0f), List.of(3.0f));
    }
}