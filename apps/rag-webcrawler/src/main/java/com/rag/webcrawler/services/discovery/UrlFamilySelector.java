package com.rag.webcrawler.services.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
        Map<String, List<String>> families = new LinkedHashMap<>();
        for (var url : urls) {
            var key = familyKey(url);
            if (key != null) families.computeIfAbsent(key, _ -> new ArrayList<>()).add(url);
        }
        var selected = new ArrayList<String>(families.size());
        for (var family : families.values()) selected.add(representative(family, startKey));
        return selected;
    }

    private static String representative(List<String> family, String startKey) {
        for (var url : family) {
            if (startKey != null && startKey.equals(canonicalKey(url))) return url;
        }
        return family.stream().min(UrlFamilySelector::compare).orElse(family.getFirst());
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
        var parts = new ArrayList<String>();
        for (var segment : pathOf(url).split(SLASH)) {
            if (!segment.isEmpty() && !isReleaseSegment(segment)) parts.add(segment);
        }
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
}
