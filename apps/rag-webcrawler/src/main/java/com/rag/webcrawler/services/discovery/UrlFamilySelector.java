package com.rag.webcrawler.services.discovery;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collapses the same document published under several release channels
 * (versioned directories, {@code latest}/{@code stable} aliases) into one
 * representative. The heuristic is generic: a whole path segment that looks
 * like a release identifier is masked, so it works for any documentation tree
 * without naming a specific product or version.
 */
public final class UrlFamilySelector {

    private static final Pattern CHANNEL_SEGMENT = Pattern.compile(
            "(?i)^(?:rc\\d*|alpha|beta|snapshot|nightly|dev|next|latest|stable|current|main|master)$");
    private static final Pattern VERSION_SEGMENT = Pattern.compile("(?i)^v?\\d[\\w.-]*$");
    private static final String SLASH = "/";

    public List<String> select(List<String> urls, String startUrl) {
        if (urls == null || urls.isEmpty()) return List.of();
        var startKey = canonicalKey(startUrl);
        return urls.stream()
                .map(url -> new Family(familyKey(url), url))
                .filter(family -> family.key() != null)
                .collect(Collectors.groupingBy(Family::key, LinkedHashMap::new,
                        Collectors.mapping(Family::url, Collectors.toList())))
                .values().stream()
                .map(family -> representative(family, startKey))
                .toList();
    }

    private static String representative(List<String> family, String startKey) {
        return family.stream()
                .filter(url -> startKey != null && startKey.equals(canonicalKey(url)))
                .findFirst()
                .orElseGet(() -> family.stream().min(UrlFamilySelector::compare).orElse(family.getFirst()));
    }

    private static int compare(String left, String right) {
        var leftPath = pathOf(left);
        var rightPath = pathOf(right);
        if (leftPath.length() != rightPath.length()) return Integer.compare(leftPath.length(), rightPath.length());
        return leftPath.compareTo(rightPath);
    }

    private static String familyKey(String url) {
        var uri = uriOf(url);
        if (uri == null) return null;
        var parts = Stream.of(pathOf(url).split(SLASH))
                .filter(segment -> !segment.isEmpty() && !isReleaseSegment(segment))
                .toList();
        return uri.getAuthority() + SLASH + String.join(SLASH, parts);
    }

    private static boolean isReleaseSegment(String segment) {
        return CHANNEL_SEGMENT.matcher(segment).matches()
                || VERSION_SEGMENT.matcher(segment.toLowerCase(Locale.ROOT)).matches();
    }

    private static String canonicalKey(String url) {
        try {
            return UrlScope.of(url).canonical(url);
        } catch (RuntimeException _) {
            return url;
        }
    }

    private static String pathOf(String url) {
        var uri = uriOf(url);
        return uri == null || uri.getPath() == null ? "" : uri.getPath();
    }

    private static URI uriOf(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    /** A URL paired with the release-masked key of the family it belongs to. */
    private record Family(String key, String url) {
    }
}
