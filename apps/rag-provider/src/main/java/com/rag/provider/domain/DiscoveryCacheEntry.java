package com.rag.provider.domain;

/**
 * One discovery-cache record. A null {@code host} is a negative entry: the last
 * scan found nothing, and the negative TTL avoids rescanning on every retry.
 */
public record DiscoveryCacheEntry(String host, long checkedAtEpochSeconds) {
}
