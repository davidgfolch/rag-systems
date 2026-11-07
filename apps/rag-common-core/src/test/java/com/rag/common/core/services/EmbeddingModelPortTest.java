package com.rag.common.core.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingModelPortTest {

    @Test
    void defaultBatchEmbedDelegatesToSingleEmbed() {
        EmbeddingModelPort port = text -> new float[]{(float) text.length()};
        assertThat(port.embed(List.of("ab", "cde")))
                .containsExactly(new float[]{2.0f}, new float[]{3.0f});
    }
}