package com.rag.tui.client;

import com.rag.contract.model.CrawlStartRequest;
import com.rag.contract.model.CrawlStartResponse;
import com.rag.contract.model.CrawlStepRequest;
import com.rag.contract.model.CrawlStepResponse;
import com.rag.contract.model.IngestRequest;
import com.rag.contract.model.IngestResponse;
import com.rag.contract.model.IngestJobResponse;
import com.rag.contract.model.IngestStatusDTO;
import com.rag.contract.model.IngestUrlRequest;
import com.rag.contract.model.QueryRequest;
import com.rag.contract.model.QueryResponse;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.contract.pipeline.PipelineVariantDTO;
import com.rag.tui.launcher.ModuleRegistry;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.rag.contract.constants.ApiPaths.CRAWL;
import static com.rag.contract.constants.ApiPaths.CRAWL_BY_ID;
import static com.rag.contract.constants.ApiPaths.CRAWL_STEP;
import static com.rag.contract.constants.ApiPaths.DOCUMENT_BY_ID;
import static com.rag.contract.constants.ApiPaths.DOCUMENTS;
import static com.rag.contract.constants.ApiPaths.INGEST;
import static com.rag.contract.constants.ApiPaths.INGEST_FILE;
import static com.rag.contract.constants.ApiPaths.INGEST_FILE_ASYNC;
import static com.rag.contract.constants.ApiPaths.INGEST_STATUS;
import static com.rag.contract.constants.ApiPaths.INGEST_URL;
import static com.rag.contract.constants.ApiPaths.MODULE_VARIANTS_ACTIVE;
import static com.rag.contract.constants.ApiPaths.QUERY;
import static com.rag.contract.constants.ApiPaths.moduleVariants;
import static com.rag.contract.constants.ApiPaths.variantActivate;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;

/**
 * REST client for the active rag-* module: ingestion and query endpoints,
 * using the shared contract DTOs. The active module is resolved per call.
 */
public class RagApiClient {

    private static final Logger log = LoggerFactory.getLogger(RagApiClient.class);

    private static final int MAX_CACHED_CLIENTS = 16;

    private final ModuleRegistry registry;
    private final RestClient.Builder builder;
    private final RestClient.Builder crawlBuilder;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();
    private final Map<String, RestClient> crawlClients = new ConcurrentHashMap<>();

    public RagApiClient(ModuleRegistry registry, RestClient.Builder builder) {
        this(registry, builder, builder);
    }

    public RagApiClient(ModuleRegistry registry, RestClient.Builder builder, RestClient.Builder crawlBuilder) {
        this.registry = registry;
        this.builder = builder;
        this.crawlBuilder = crawlBuilder;
    }

    public IngestResponse ingest(String content, Map<String, Object> metadata) {
        var request = new IngestRequest().content(content);
        if (metadata != null) {
            request.metadata(metadata);
        }
        return post(INGEST, request, IngestResponse.class);
    }

    public IngestResponse ingestFile(byte[] bytes, String fileName, Map<String, Object> metadata) {
        return client()
                .post().uri(INGEST_FILE)
                .contentType(MULTIPART_FORM_DATA)
                .body(buildBody(bytes, fileName, metadata))
                .retrieve().body(IngestResponse.class);
    }

    @Nonnull
    private static MultiValueMap<String, Object> buildBody(byte[] bytes, String fileName, Map<String, Object> metadata) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        var jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(APPLICATION_JSON);
        body.add("fileInfo", new HttpEntity<>(metadata, jsonHeaders));
        return body;
    }

    /**
     * Submits a file for asynchronous ingestion; returns immediately with a job
     * id that the caller can poll via {@link #ingestStatus(String)}.
     */
    public IngestJobResponse submitIngestFile(byte[] bytes, String fileName, Map<String, Object> metadata) {
        return client()
                .post().uri(INGEST_FILE_ASYNC)
                .contentType(MULTIPART_FORM_DATA)
                .body(buildBody(bytes, fileName, metadata))
                .retrieve().body(IngestJobResponse.class);
    }

    public IngestStatusDTO ingestStatus(String documentId) {
        log.debug("Polling ingest status for {}", documentId);
        return client().get().uri(INGEST_STATUS, documentId)
                .retrieve().body(IngestStatusDTO.class);
    }

    public IngestResponse ingestUrl(String url) {
        return post(INGEST_URL,
                new IngestUrlRequest().url(URI.create(url)), IngestResponse.class);
    }

    /** Starts an interactive crawl session and returns the root page to prompt for. */
    public CrawlStartResponse startCrawl(String url) {
        return crawlClient().post().uri(CRAWL)
                .body(new CrawlStartRequest().url(URI.create(url)))
                .retrieve().body(CrawlStartResponse.class);
    }

    /** Starts a crawl with an optional page cap and discovery toggle. */
    public CrawlStartResponse startCrawl(String url, Integer maxPages, boolean discover) {
        var request = new CrawlStartRequest().url(URI.create(url)).discover(discover);
        if (maxPages != null) request.maxPages(maxPages);
        return crawlClient().post().uri(CRAWL).body(request).retrieve().body(CrawlStartResponse.class);
    }

    /** Submits the current crawl page and resolves the next one. */
    public CrawlStepResponse stepCrawl(String sessionId, String question) {
        return stepCrawl(sessionId, question, false);
    }

    /** Submits the current crawl page, optionally overriding an already-ingested source. */
    public CrawlStepResponse stepCrawl(String sessionId, String question, boolean override) {
        return crawlClient().post().uri(CRAWL_STEP, sessionId)
                .body(new CrawlStepRequest().question(question).override(override))
                .retrieve().body(CrawlStepResponse.class);
    }

    /** Cancels a crawl session; pages already submitted keep ingesting. */
    public void cancelCrawl(String sessionId) {
        crawlClient().delete().uri(CRAWL_BY_ID, sessionId).retrieve().toBodilessEntity();
    }

    /**
     * Deletes an ingested document and all of its chunks from the active module.
     */
    public void deleteDocument(String documentId) {
        client().delete().uri(DOCUMENT_BY_ID, documentId)
                .retrieve().toBodilessEntity();
    }

    /**
     * Deletes an ingested document and all of its chunks from a specific module.
     */
    public void deleteDocument(String baseUrl, String documentId) {
        builder.clone().baseUrl(baseUrl).build()
                .delete().uri(DOCUMENT_BY_ID, documentId)
                .retrieve().toBodilessEntity();
    }

    public QueryResponse query(String question, int topK) {
        return post(QUERY,
                new QueryRequest().question(question).topK(topK), QueryResponse.class);
    }

    /**
     * Lists ingested documents from a specific module, bypassing the active-module
     * indirection so the TUI can probe every reachable rag-* instance as the
     * {@code documents} command does.
     */
    public List<DocumentSummaryDTO> listDocuments(String baseUrl) {
        try {
            var documents = builder.clone().baseUrl(baseUrl).build()
                    .get().uri(DOCUMENTS)
                    .retrieve().body(DocumentSummaryDTO[].class);
            return documents == null ? List.of() : List.of(documents);
        } catch (HttpClientErrorException.NotFound _) {
            return List.of();
        }
    }

    /** Lists the pipeline variants registered on a module. */
    public List<PipelineVariantDTO> variants(String module) {
        var variants = client().get().uri(moduleVariants(module))
                .retrieve().body(PipelineVariantDTO[].class);
        return variants == null ? List.of() : List.of(variants);
    }

    /** Activates one pipeline variant on a module; its store is used from the next request on. */
    public PipelineVariantDTO activateVariant(String module, String variantId) {
        return client().post().uri(variantActivate(module, variantId))
                .retrieve().body(PipelineVariantDTO.class);
    }

    /** The variant a module currently serves, empty when none is active. */
    public Optional<PipelineVariantDTO> activeVariant(String module) {
        return Optional.ofNullable(client().get().uri(MODULE_VARIANTS_ACTIVE, module)
                .retrieve().body(PipelineVariantDTO.class));
    }

    /** Registers a new pipeline variant on a module and returns it with its generated id. */
    public PipelineVariantDTO createVariant(String module, PipelineVariantDTO request) {
        return client().post().uri(moduleVariants(module)).body(request)
                .retrieve().body(PipelineVariantDTO.class);
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return client().post().uri(path).body(body).retrieve().body(responseType);
    }

    private RestClient client() {
        var baseUrl = registry.active().baseUrl();
        if (clients.size() >= MAX_CACHED_CLIENTS) {
            clients.clear();
        }
        return clients.computeIfAbsent(baseUrl, url -> builder.clone().baseUrl(url).build());
    }

    /** Crawl calls may wait for the module's ingestion queue, so they use a longer read timeout. */
    private RestClient crawlClient() {
        var baseUrl = registry.active().baseUrl();
        if (crawlClients.size() >= MAX_CACHED_CLIENTS) {
            crawlClients.clear();
        }
        return crawlClients.computeIfAbsent(baseUrl, url -> crawlBuilder.clone().baseUrl(url).build());
    }
}
