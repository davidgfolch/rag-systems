package com.rag.common.app.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActiveEmbeddingModelPortTest {

    private final VariantStore store = mock(VariantStore.class);
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final ObjectProvider<VariantStore> provider = provider();
    private final ActiveEmbeddingModelPort sut = new ActiveEmbeddingModelPort(provider);

    @SuppressWarnings("unchecked")
    private static ObjectProvider<VariantStore> provider() {
        return mock(ObjectProvider.class);
    }

    @BeforeEach
    void setUp() {
        when(provider.getObject()).thenReturn(store);
        when(store.embeddingModel()).thenReturn(model);
    }

    @Test
    void embedsSingleTextsAndBatchesWithTheActiveVariantsModel() {
        var embedding = new Embedding(new float[]{1f, 2f, 3f}, 0);
        when(model.call(any(EmbeddingRequest.class))).thenReturn(new EmbeddingResponse(List.of(embedding)));

        assertThat(sut.embed("hello")).containsExactly(1f, 2f, 3f);
        assertThat(sut.embed(List.of("hello"))).hasSize(1);
    }
}
