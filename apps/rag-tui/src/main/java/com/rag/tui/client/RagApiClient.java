package com.rag.tui.client;

import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestStatusDTO;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.QueryRequest;
import com.rag.contract.model.QueryResponse;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.tui.launcher.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * REST client for the active rag-* module: ingestion and query endpoints,
 * using the shared contract DTOs. The active module is resolved per call.
 */
public class RagApiClient {

    private static final Logger log = LoggerFactory.getLogger(RagApiClient.class);

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

    public IngestResponse ingestFile(byte[] bytes, String fileName, Map<String, Object> metadata) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("fileInfo", new HttpEntity<>(metadata, jsonHeaders));
        return client()
                .post().uri("/api/documents/ingest-file")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(IngestResponse.class);
    }

    /**
     * Submits a file for asynchronous ingestion; returns immediately with a job
     * id that the caller can poll via {@link #ingestStatus(String)}.
     */
    public IngestJobResponse submitIngestFile(byte[] bytes, String fileName, Map<String, Object> metadata) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("fileInfo", new HttpEntity<>(metadata, jsonHeaders));
        return client()
                .post().uri("/api/documents/ingest-file-async")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(IngestJobResponse.class);
    }

    public IngestStatusDTO ingestStatus(String documentId) {
        log.debug("Polling ingest status for {}", documentId);
        return client().get().uri("/api/documents/ingest-status/{id}", documentId)
                .retrieve().body(IngestStatusDTO.class);
    }

    public IngestResponse ingestUrl(String url) {
        return post("/api/documents/ingest-url",
                new IngestUrlRequest().url(URI.create(url)), IngestResponse.class);
    }

    public QueryResponse query(String question, int topK) {
        return post("/api/query",
                new QueryRequest().question(question).topK(topK), QueryResponse.class);
    }

    /**
     * Lists ingested documents from a specific module, bypassing the active-module
     * indirection so the TUI can probe every reachable rag-* instance as the
     * {@code documents} command does.
     */
    public List<DocumentSummaryDTO> listDocuments(String baseUrl) {
        DocumentSummaryDTO[] documents = builder.clone().baseUrl(baseUrl).build()
                .get().uri("/api/documents")
                .retrieve().body(DocumentSummaryDTO[].class);
        return documents == null ? List.of() : List.of(documents);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return client().post().uri(path).body(body).retrieve().body(responseType);
    }

    private RestClient client() {
        return builder.clone().baseUrl(registry.active().baseUrl()).build();
    }
}