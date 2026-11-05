package com.rag.common.app.pipeline;

import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.generation.adapter.SpringAiEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

/**
 * Module-wide embedding port that resolves the active variant's embedding model
 * per call, so ingesting a variant embeds its chunks with the variant's model
 * instead of the global one.
 *
 * <p>The store is resolved lazily through an {@link ObjectProvider}: the boot
 * vector store depends on this port, and the variant store depends on the boot
 * store, so holding the store directly would create a bean cycle.
 */
public class ActiveEmbeddingModelPort implements EmbeddingModelPort {

    private final ObjectProvider<VariantStore> store;

    public ActiveEmbeddingModelPort(ObjectProvider<VariantStore> store) {
        this.store = store;
    }

    @Override
    public List<Float> embed(String text) {
        return adapter().embed(text);
    }

    @Override
    public List<List<Float>> embed(List<String> texts) {
        return adapter().embed(texts);
    }

    private EmbeddingModelPort adapter() {
        return new SpringAiEmbeddingModel(store.getObject().embeddingModel());
    }
}
