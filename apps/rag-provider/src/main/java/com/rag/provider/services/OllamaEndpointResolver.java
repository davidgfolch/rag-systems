package com.rag.provider.services;

import com.rag.provider.domain.OllamaDiscoverySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Set;

/**
 * Turns an {@code OLLAMA_BASE_URL} spec into a usable base URL. {@code auto}
 * and {@code discover} trigger {@link OllamaDiscoveryService}; {@code local}
 * means loopback without a scan; anything else is treated as an explicit URL
 * and never probes or scans, so offline boots stay fast.
 */
public class OllamaEndpointResolver {

    private static final Logger log = LoggerFactory.getLogger(OllamaEndpointResolver.class);
    private static final String LOOPBACK = "127.0.0.1";
    private static final Set<String> LOCAL = Set.of("local", "localhost", LOOPBACK);
    private static final Set<String> AUTO = Set.of("auto", "discover");
    private static final String SCHEME = "http://";
    private static final String SCHEME_SEPARATOR = "://";
    private static final String PORT_SEPARATOR = ":";
    private static final String SLASH = "/";

    private final OllamaDiscoverySettings settings;
    private final OllamaDiscoveryService discovery;

    public OllamaEndpointResolver(OllamaDiscoverySettings settings, OllamaDiscoveryService discovery) {
        this.settings = settings;
        this.discovery = discovery;
    }

    /** Resolves {@code spec}; never throws and always returns a base URL. */
    public String resolve(String spec) {
        var value = spec == null ? "" : spec.strip();
        var normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || AUTO.contains(normalized)) return resolveAuto();
        if (LOCAL.contains(normalized)) return baseUrl(LOOPBACK);
        return normalizeExplicit(value);
    }

    private String resolveAuto() {
        var host = discovery.discover();
        if (host.isPresent()) return baseUrl(host.get());
        var fallback = baseUrl(LOOPBACK);
        log.warn("Ollama auto-discovery found no server; falling back to {}", fallback);
        return fallback;
    }

    private String baseUrl(String host) {
        return SCHEME + host + PORT_SEPARATOR + settings.port();
    }

    private String normalizeExplicit(String value) {
        if (value.contains(SCHEME_SEPARATOR)) return trimTrailingSlashes(value);
        if (value.contains(PORT_SEPARATOR)) return SCHEME + trimTrailingSlashes(value);
        return baseUrl(value);
    }

    private static String trimTrailingSlashes(String value) {
        var trimmed = value;
        while (trimmed.endsWith(SLASH)) {
            trimmed = trimmed.substring(0, trimmed.length() - SLASH.length());
        }
        return trimmed;
    }
}
