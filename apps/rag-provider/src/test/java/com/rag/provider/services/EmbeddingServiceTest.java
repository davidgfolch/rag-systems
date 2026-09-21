package com.rag.provider.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock private ModelRouter router;
    @Mock private EmbeddingModel embeddingModel;

    @Test
    void shouldEmbedTextsAndBoxVectors() {
        when(router.embeddingModel()).thenReturn(embeddingModel);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(
                new EmbeddingResponse(List.of(new Embedding(new float[]{1f, 2f}, 0))));
        when(router.embeddingDimension()).thenReturn(2);
        var vectors = new EmbeddingService(router).embed(List.of("a", "b"));
        assertThat(vectors).containsExactly(List.of(1f, 2f));
    }

    @Test
    void shouldWindowOversizedBatches() {
        when(router.embeddingModel()).thenReturn(embeddingModel);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            var req = invocation.getArgument(0, EmbeddingRequest.class);
            var embeddings = new ArrayList<Embedding>();
            for (int i = 0; i < req.getInstructions().size(); i++) {
                embeddings.add(new Embedding(new float[]{req.getInstructions().size(), i}, i));
            }
            return new EmbeddingResponse(embeddings);
        });
        when(router.embeddingDimension()).thenReturn(2);
        var vectors = new EmbeddingService(router, 2).embed(List.of("a", "b", "c"));
        verify(embeddingModel, times(2)).call(any(EmbeddingRequest.class));
        assertThat(vectors).containsExactly(
                List.of(2f, 0f), List.of(2f, 1f), List.of(1f, 0f));
    }

    @Test
    void shouldRejectNonPositiveBatchSize() {
        assertThatThrownBy(() -> new EmbeddingService(router, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("embedding batch size must be positive");
    }
}