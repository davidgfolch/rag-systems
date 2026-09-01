package com.rag.common.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiEmbeddingModelTest {

    private final org.springframework.ai.embedding.EmbeddingModel delegate =
            mock(org.springframework.ai.embedding.EmbeddingModel.class);
    private final SpringAiEmbeddingModel model = new SpringAiEmbeddingModel(delegate);

    @Test
    void embedsTextAndReturnsFloatList() {
        when(delegate.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[]{1.0f, 2.0f, 0.5f}, 0))));

        List<Float> result = model.embed("hello");

        assertThat(result).containsExactly(1.0f, 2.0f, 0.5f);
    }
}