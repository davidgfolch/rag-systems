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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return reachableModules()
                .flatMap(module -> matches(module, contentHash))
                .findFirst();
    }

    /**
     * Indexes every ingested document by its stable source key (host + path) in
     * one pass over the reachable modules, so a crawl can check its whole
     * frontier without listing documents again per page.
     */
    public Map<String, Match> sourceIndex() {
        return reachableModules()
                .flatMap(this::indexedDocuments)
                .filter(entry -> entry.key() != null)
                .collect(Collectors.toMap(SourceEntry::key, SourceEntry::match,
                        (first, _) -> first, LinkedHashMap::new));
    }

    private Stream<Match> matches(Module module, String contentHash) {
        return apiClient.listDocuments(module.baseUrl()).stream()
                .filter(doc -> contentHash.equals(metadataValue(doc, CONTENT_HASH)))
                .map(doc -> new Match(doc.getDocumentId(), module.name(), module.baseUrl()));
    }

    private Stream<SourceEntry> indexedDocuments(Module module) {
        return apiClient.listDocuments(module.baseUrl()).stream()
                .map(doc -> new SourceEntry(WebUrls.key(metadataValue(doc, SOURCE)),
                        new Match(doc.getDocumentId(), module.name(), module.baseUrl())));
    }

    private Stream<Module> reachableModules() {
        return registry.modules().stream().filter(module -> healthClient.isUp(module.baseUrl()));
    }

    private static String metadataValue(DocumentSummaryDTO doc, String key) {
        Map<String, Object> metadata = doc.getMetadata();
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : value.toString();
    }

    /** A document's source key paired with the module it was found on. */
    private record SourceEntry(String key, Match match) {
    }
}
