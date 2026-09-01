package com.rag.tui.client;

import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.QueryRequest;
import com.rag.contract.model.QueryResponse;
import com.rag.tui.launcher.ModuleRegistry;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

/**
 * REST client for the active rag-* module: ingestion and query endpoints,
 * using the shared contract DTOs. The active module is resolved per call.
 */
public class RagApiClient {

    private final ModuleRegistry registry;
    private final RestClient.Builder builder;

    public RagApiClient(ModuleRegistry registry, RestClient.Builder builder) {
        this.registry = registry;
        this.builder = builder;
    }

    public IngestResponse ingest(String content, Map<String, Object> metadata) {
        IngestRequest request = new IngestRequest().content(content);
        if (metadata != null) {
            request.metadata(metadata);
        }
        return post("/api/documents/ingest", request, IngestResponse.class);
    }

    public IngestResponse ingestUrl(String url) {
        return post("/api/documents/ingest-url",
                new IngestUrlRequest().url(URI.create(url)), IngestResponse.class);
    }

    public QueryResponse query(String question, int topK) {
        return post("/api/query",
                new QueryRequest().question(question).topK(topK), QueryResponse.class);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return client().post().uri(path).body(body).retrieve().body(responseType);
    }

    private RestClient client() {
        return builder.clone().baseUrl(registry.active().baseUrl()).build();
    }
}