package com.rag.tui.adapter;

import com.rag.common.services.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiEmbeddingModelTest {

    private final org.springframework.ai.embedding.EmbeddingModel delegate =
            mock(org.springframework.ai.embedding.EmbeddingModel.class);

    private final EmbeddingModel sut = new SpringAiEmbeddingModel(delegate);

    @Test
    void embedsTextViaDelegate() {
        when(delegate.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[]{1f, 2f}, 0))));

        List<Float> embedding = sut.embed("hello");

        assertThat(embedding).containsExactly(1.0f, 2.0f);
        verify(delegate).call(any(EmbeddingRequest.class));
    }
}
