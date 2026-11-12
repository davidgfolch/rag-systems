package com.rag.tui.ui;

import com.rag.common.core.util.WebUrls;
import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static com.rag.common.core.domain.MetadataKeys.CONTENT_HASH;
import static com.rag.common.core.domain.MetadataKeys.SOURCE;

/**
 * Finds already-ingested documents so re-ingestion can be de-duplicated.
 * Files match by SHA-256 content hash, URLs by domain + URI (scheme,
 * fragment and trailing-slash insensitive). Only metadata of reachable
 * modules is consulted; a missing or unreadable field simply yields "no
 * duplicate" rather than blocking ingestion.
 */
public class DuplicateFinder {

    private final ModuleRegistry registry;
    private final RagApiClient apiClient;
    private final ModuleHealthClient healthClient;

    public DuplicateFinder(ModuleRegistry registry, RagApiClient apiClient, ModuleHealthClient healthClient) {
        this.registry = registry;
        this.apiClient = apiClient;
        this.healthClient = healthClient;
    }

    /** A duplicate found on a specific module, with that module's REST base url. */
    public record Match(String documentId, String moduleName, String baseUrl) {
    }

    /**
     * Returns the first document whose {@code contentHash} metadata equals the
     * given hash, or empty when none is ingested yet.
     */
    public Optional<Match> findByContentHash(String contentHash) {
        if (contentHash == null) return Optional.empty();
        for (Module module : registry.modules()) {
            if (!healthClient.isUp(module.baseUrl())) continue;
            var match = apiClient.listDocuments(module.baseUrl()).stream()
                    .filter(d -> contentHash.equals(metadataValue(d, CONTENT_HASH)))
                    .findFirst()
                    .map(d -> new Match(d.getDocumentId(), module.name(), module.baseUrl()));
            if (match.isPresent()) return match;
        }
        return Optional.empty();
    }

    /**
     * Indexes every ingested document by its stable source key (host + path) in
     * one pass over the reachable modules, so a crawl can check its whole
     * frontier without listing documents again per page.
     */
    public Map<String, Match> sourceIndex() {
        var index = new LinkedHashMap<String, Match>();
        for (Module module : registry.modules()) {
            if (!healthClient.isUp(module.baseUrl())) continue;
            for (var doc : apiClient.listDocuments(module.baseUrl())) {
                var key = WebUrls.key(metadataValue(doc, SOURCE));
                if (key != null) {
                    index.putIfAbsent(key, new Match(doc.getDocumentId(), module.name(), module.baseUrl()));
                }
            }
        }
        return index;
    }

    private static String metadataValue(DocumentSummaryDTO doc, String key) {
        Map<String, Object> metadata = doc.getMetadata();
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : value.toString();
    }
}
