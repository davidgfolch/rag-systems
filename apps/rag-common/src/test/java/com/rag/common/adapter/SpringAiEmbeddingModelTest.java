package com.rag.common.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiEmbeddingModelTest {

    private final EmbeddingModel delegate = mock(EmbeddingModel.class);
    private final SpringAiEmbeddingModel model = new SpringAiEmbeddingModel(delegate);

    @Test
    void embedsTextAndReturnsFloatList() {
        when(delegate.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[]{1.0f, 2.0f, 0.5f}, 0))));
        var result = model.embed("hello");
        assertThat(result).containsExactly(1.0f, 2.0f, 0.5f);
    }

    @Test
    void embedsBatchOfTextsInSingleCall() {
        when(delegate.call(argThat(r -> r.getInstructions().equals(List.of("a", "b")))))
                .thenReturn(new EmbeddingResponse(List.of(
                        new Embedding(new float[]{1.0f, 2.0f}, 0),
                        new Embedding(new float[]{3.0f, 4.0f}, 1))));
        var result = model.embed(List.of("a", "b"));
        assertThat(result).containsExactly(List.of(1.0f, 2.0f), List.of(3.0f, 4.0f));
    }
}