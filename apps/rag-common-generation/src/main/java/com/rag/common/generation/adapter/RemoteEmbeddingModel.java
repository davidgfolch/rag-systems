package com.rag.common.generation.adapter;

import com.rag.common.core.domain.FloatConversions;
import com.rag.contract.provider.EmbedRequest;
import com.rag.contract.provider.EmbedResponse;
import com.rag.contract.provider.ProviderStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static com.rag.contract.constants.ApiPaths.EMBED;
import static com.rag.contract.constants.ApiPaths.PROVIDER;

/**
 * Spring AI {@link EmbeddingModel} backed by the rag-provider service's
 * {@code /api/embed} endpoint. Without a model id it uses the provider's active
 * embedding model and caches its dimension from {@code /api/provider}; with a
 * model id it asks the provider for that specific model, which is how a
 * pipeline variant embeds with its own model while the global one stays put.
 */
public class RemoteEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(RemoteEmbeddingModel.class);

    private final ProviderHttpClient httpClient;
    private final int fallbackDimension;
    private final String modelId;
    private volatile int cachedDimension = -1;

    public RemoteEmbeddingModel(ProviderHttpClient httpClient) {
        this(httpClient, 768, null);
    }

    public RemoteEmbeddingModel(ProviderHttpClient httpClient, int fallbackDimension) {
        this(httpClient, fallbackDimension, null);
    }

    public RemoteEmbeddingModel(ProviderHttpClient httpClient, int fallbackDimension, String modelId) {
        this.httpClient = httpClient;
        this.fallbackDimension = fallbackDimension;
        this.modelId = modelId;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var res = httpClient.postJson(EMBED,
                new EmbedRequest(modelId, request.getInstructions()), EmbedResponse.class);
        var results = new java.util.ArrayList<Embedding>(res.embeddings().size());
        for (int i = 0; i < res.embeddings().size(); i++) {
            var vector = FloatConversions.toFloatArray(res.embeddings().get(i));
            results.add(new Embedding(vector, i));
            cacheDimension(vector.length);
            cacheProviderDimension();
        }
        log.debug("Embedded {} texts via remote provider (model {})", results.size(),
                modelId == null || modelId.isBlank() ? "(active)" : modelId);
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        var res = httpClient.postJson(EMBED,
                new EmbedRequest(modelId, List.of(document.getText())), EmbedResponse.class);
        var vector = FloatConversions.toFloatArray(res.embeddings().getFirst());
        cacheDimension(vector.length);
        cacheProviderDimension();
        return vector;
    }

    @Override
    public int dimensions() {
        if (modelId == null || modelId.isBlank()) {
            return activeProviderDimension();
        }
        if (cachedDimension < 0) {
            call(new EmbeddingRequest(List.of("dimension-probe"), null));
        }
        return cachedDimension;
    }

    private int activeProviderDimension() {
        if (cachedDimension < 0) {
            try {
                var status = httpClient.get(PROVIDER, ProviderStatusDTO.class);
                cachedDimension = status.embeddingDimension();
                log.info("Remote embedding dimension resolved: {}", cachedDimension);
            } catch (RuntimeException e) {
                if (fallbackDimension <= 0) {
                    throw e;
                }
                log.warn("Provider unreachable, using default embedding dimension {} "
                                + "(real value resolved on first embed): {}",
                        fallbackDimension, e.getMessage());
                return fallbackDimension;
            }
        }
        return cachedDimension;
    }

    /** A variant model's dimension comes from its own vectors. */
    private void cacheDimension(int dimension) {
        if (modelId != null && !modelId.isBlank() && cachedDimension < 0 && dimension > 0) {
            cachedDimension = dimension;
        }
    }

    /** The active model's dimension is authoritative on the provider status. */
    private void cacheProviderDimension() {
        if (modelId == null || modelId.isBlank()) {
            try {
                dimensions();
            } catch (RuntimeException e) {
                log.debug("Embedding dimension fetch deferred: {}", e.getMessage());
            }
        }
    }
}
