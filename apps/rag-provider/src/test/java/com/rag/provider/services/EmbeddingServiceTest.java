package com.rag.provider.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}