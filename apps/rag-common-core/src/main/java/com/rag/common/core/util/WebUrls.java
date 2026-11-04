package com.rag.common.core.util;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Normalizes web URLs to a stable key so the same page is not ingested twice.
 * The key is host + path, ignoring scheme, query, fragment, case of the host
 * and a trailing slash.
 */
public final class WebUrls {

    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String ROOT_PATH = "/";

    private WebUrls() {}

    /** Parses an http(s) URL, or empty when it is not a usable web address. */
    public static Optional<URI> parse(String rawUrl) {
        if (rawUrl == null) return Optional.empty();
        try {
            var uri = URI.create(rawUrl);
            var scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase(HTTP) || scheme.equalsIgnoreCase(HTTPS))) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    /** Stable de-duplication key for a raw URL, or null when it is not http(s). */
    public static String key(String rawUrl) {
        return parse(rawUrl).map(WebUrls::keyOf).orElse(null);
    }

    /** Stable de-duplication key for an already parsed URL. */
    public static String keyOf(URI uri) {
        return hostOf(uri) + pathOf(uri);
    }

    /** Lower-cased host of a raw URL, or an empty string when it has none. */
    public static String host(String rawUrl) {
        return parse(rawUrl).map(WebUrls::hostOf).orElse("");
    }

    public static String hostOf(URI uri) {
        var host = uri.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    public static String pathOf(URI uri) {
        var path = uri.getPath();
        if (path == null || path.isEmpty()) return ROOT_PATH;
        return path.length() > 1 && path.endsWith(ROOT_PATH)
                ? path.substring(0, path.length() - 1) : path;
    }
}
