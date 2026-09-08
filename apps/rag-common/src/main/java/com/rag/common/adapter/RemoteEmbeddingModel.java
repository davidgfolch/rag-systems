package com.rag.common.adapter;

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

/**
 * Spring AI {@link EmbeddingModel} backed by the rag-provider service's
 * {@code /api/embed} endpoint. The vector dimension is fetched once from
 * {@code /api/provider} and cached.
 */
public class RemoteEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(RemoteEmbeddingModel.class);

    private final ProviderHttpClient httpClient;
    private volatile int cachedDimension = -1;

    public RemoteEmbeddingModel(ProviderHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var response = httpClient.postJson("/api/embed",
                new EmbedRequest(request.getInstructions()), EmbedResponse.class);
        cacheDimension();
        var results = new java.util.ArrayList<Embedding>(response.embeddings().size());
        for (int i = 0; i < response.embeddings().size(); i++) {
            results.add(new Embedding(toFloatArray(response.embeddings().get(i)), i));
        }
        log.debug("Embedded {} texts via remote provider", results.size());
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        var response = httpClient.postJson("/api/embed",
                new EmbedRequest(List.of(document.getText())), EmbedResponse.class);
        return toFloatArray(response.embeddings().get(0));
    }

    @Override
    public int dimensions() {
        if (cachedDimension < 0) {
            var status = httpClient.get("/api/provider", ProviderStatusDTO.class);
            cachedDimension = status.embeddingDimension();
            log.info("Remote embedding dimension resolved: {}", cachedDimension);
        }
        return cachedDimension;
    }

    private void cacheDimension() {
        if (cachedDimension < 0) {
            try {
                dimensions();
            } catch (Exception e) {
                log.debug("Embedding dimension fetch deferred: {}", e.getMessage());
            }
        }
    }

    private static float[] toFloatArray(List<Float> values) {
        var result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}