package com.rag.tui.ui;

import com.rag.contract.model.DocumentSummaryDTO;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
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

    private static final Logger log = LoggerFactory.getLogger(DuplicateFinder.class);
    private static final String HTTP = "http";
    private static final String HTTPS = "https";

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
     * Returns the first document whose {@code source} metadata is the same web
     * page as {@code rawUrl} (same host + path, ignoring scheme, fragment and
     * trailing slash), or empty when none is ingested yet. Documents whose
     * source is not an http(s) URI (e.g. local files) never match.
     */
    public Optional<Match> findBySource(String rawUrl) {
        var candidate = parseWebUri(rawUrl);
        if (candidate.isEmpty()) return Optional.empty();
        var target = candidate.get();
        for (Module module : registry.modules()) {
            if (!healthClient.isUp(module.baseUrl())) continue;
            var match = apiClient.listDocuments(module.baseUrl()).stream()
                    .filter(d -> sameWebPage(metadataValue(d, SOURCE), target))
                    .findFirst()
                    .map(d -> new Match(d.getDocumentId(), module.name(), module.baseUrl()));
            if (match.isPresent()) return match;
        }
        return Optional.empty();
    }

    private static Optional<URI> parseWebUri(String rawUrl) {
        try {
            var uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase(HTTP) || scheme.equalsIgnoreCase(HTTPS))) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (IllegalArgumentException e) {
            log.debug("Unparseable url '{}' treated as no duplicate: {}", rawUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean sameWebPage(String storedSource, URI target) {
        if (storedSource == null) return false;
        var stored = parseWebUri(storedSource);
        if (stored.isEmpty()) return false;
        var source = stored.get();
        return hostOf(source).equals(hostOf(target))
                && pathOf(source).equals(pathOf(target));
    }

    private static String hostOf(URI uri) {
        var host = uri.getHost();
        return host == null ? "" : host.toLowerCase();
    }

    private static String pathOf(URI uri) {
        var path = uri.getPath();
        if (path == null || path.isEmpty()) return "/";
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String metadataValue(DocumentSummaryDTO doc, String key) {
        Map<String, Object> metadata = doc.getMetadata();
        Object value = metadata == null ? null : metadata.get(key);
        return value == null ? null : value.toString();
    }
}
