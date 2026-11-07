package com.rag.webcrawler.services.discovery;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Site-agnostic boundary for one crawl: same origin, under the start URL's
 * directory, and not a non-content asset or an interactive endpoint. Nothing
 * here knows about a particular documentation site.
 */
public final class UrlScope {

    private static final Set<String> HTML_EXTENSIONS = Set.of("html", "htm", "xhtml");
    private static final Set<String> BLOCKED_SEGMENTS = Set.of(
            "/search", "/login", "/signin", "/logout", "/cart", "/feed", "/print/", "/cdn-cgi/");
    private static final Set<String> BLOCKED_PARAMS = Set.of("print", "download", "raw");
    private static final String SCHEME_SEPARATOR = "://";
    private static final String SLASH = "/";
    private static final String DOT = ".";
    private static final String INDEX = "index.html";
    private static final String QUERY = "?";

    private final String origin;
    private final String baseDir;

    private UrlScope(String origin, String baseDir) {
        this.origin = origin;
        this.baseDir = baseDir;
    }

    public static UrlScope of(String rawUrl) {
        var uri = parse(rawUrl);
        if (uri == null) throw new IllegalArgumentException("Not an http(s) URL: " + rawUrl);
        return new UrlScope(originOf(uri), baseDirOf(pathOf(uri)));
    }

    public boolean accepts(String rawUrl) {
        var uri = parse(rawUrl);
        if (uri == null || !origin.equals(originOf(uri))) return false;
        var path = pathOf(uri).toLowerCase(Locale.ROOT);
        if (!path.startsWith(baseDir)) return false;
        return !isAsset(path) && !isBlocked(path) && !hasBlockedParam(uri);
    }

    public String canonical(String rawUrl) {
        var uri = parse(rawUrl);
        if (uri == null) return null;
        return origin + normalizePath(pathOf(uri)) + querySuffix(uri.getRawQuery());
    }

    public String baseDir() {
        return baseDir;
    }

    public String origin() {
        return origin;
    }

    private static URI parse(String rawUrl) {
        try {
            var uri = URI.create(rawUrl);
            var scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return null;
            }
            return uri.getHost() == null ? null : uri;
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static String originOf(URI uri) {
        return uri.getScheme().toLowerCase(Locale.ROOT) + SCHEME_SEPARATOR + uri.getAuthority().toLowerCase(Locale.ROOT);
    }

    private static String pathOf(URI uri) {
        var path = uri.getPath();
        return path == null || path.isEmpty() ? SLASH : path;
    }

    private static String baseDirOf(String path) {
        var lastSlash = path.lastIndexOf(SLASH);
        var last = path.substring(lastSlash + 1);
        if (last.contains(DOT)) return path.substring(0, lastSlash + 1);
        return path.endsWith(SLASH) ? path : path + SLASH;
    }

    private static boolean isAsset(String path) {
        var lastSlash = path.lastIndexOf(SLASH);
        var last = path.substring(lastSlash + 1);
        var dot = last.lastIndexOf(DOT);
        return dot >= 0 && !HTML_EXTENSIONS.contains(last.substring(dot + 1));
    }

    private static boolean isBlocked(String path) {
        return BLOCKED_SEGMENTS.stream().anyMatch(path::contains);
    }

    private static boolean hasBlockedParam(URI uri) {
        var query = uri.getRawQuery();
        if (query == null) return false;
        for (var pair : query.split("&")) {
            var name = pair.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (BLOCKED_PARAMS.contains(name)) return true;
        }
        return false;
    }

    private static String normalizePath(String path) {
        var normalized = path.replaceAll("/{2,}", SLASH);
        if (normalized.endsWith(SLASH) && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(SLASH + INDEX)) {
            normalized = normalized.substring(0, normalized.length() - INDEX.length());
            normalized = normalized.endsWith(SLASH) ? normalized : normalized + SLASH;
        }
        return normalized;
    }

    private static String querySuffix(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return "";
        var kept = Arrays.stream(rawQuery.split("&"))
                .filter(pair -> !isTracking(pair)).sorted().toList();
        return kept.isEmpty() ? "" : QUERY + String.join("&", kept);
    }

    private static boolean isTracking(String pair) {
        var name = pair.split("=", 2)[0].toLowerCase(Locale.ROOT);
        return name.startsWith("utm_") || name.equals("ref") || name.equals("source") || name.equals("fbclid");
    }
}
