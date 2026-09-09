package com.rag.tui.client;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * REST client for the rag-provider companion service: provider status, the
 * models.dev catalog (browse + refresh), and chat/embedding model switching.
 */
public class ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);

    private final String baseUrl;
    private final RestClient rest;

    public ProviderClient(String baseUrl, RestClient.Builder builder) {
        this.baseUrl = trimSlash(baseUrl);
        this.rest = builder.clone().baseUrl(this.baseUrl).build();
    }

    public ProviderStatusDTO status() {
        log.debug("Provider status requested from {}", baseUrl);
        return rest.get().uri("/api/provider").retrieve().body(ProviderStatusDTO.class);
    }

    public ModelCatalogDTO catalog() {
        log.debug("Model catalog requested from {}", baseUrl);
        return rest.get().uri("/api/provider/catalog").retrieve().body(ModelCatalogDTO.class);
    }

    public ModelCatalogDTO refreshCatalog() {
        log.debug("Model catalog refresh requested from {}", baseUrl);
        return rest.post().uri("/api/provider/catalog/refresh").retrieve().body(ModelCatalogDTO.class);
    }

    public void switchChat(String providerId, String model) {
        log.info("Chat model switch requested: providerId={}, model={}", providerId, model);
        postSwitch("/api/provider/chat", providerId, model);
    }

    public void switchEmbedding(String providerId, String model) {
        log.info("Embedding model switch requested: providerId={}, model={}", providerId, model);
        postSwitch("/api/provider/embedding", providerId, model);
    }

    public ProviderStatusDTO configure(ConfigureProviderRequest request) {
        log.info("Provider profile configure requested: providerId={}", request.providerId());
        return rest.post().uri("/api/provider/configure")
                .body(request).retrieve().body(ProviderStatusDTO.class);
    }

    private void postSwitch(String path, String providerId, String model) {
        rest.post().uri(path).body(new ModelSpecDTO(providerId, model)).retrieve().toBodilessEntity();
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}