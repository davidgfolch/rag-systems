package com.rag.provider.domain;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Location and freshness rules for the discovery cache. Positive entries keep
 * a working host for an hour by default; negative entries expire quickly so a
 * server that just came up is retried soon.
 */
public record DiscoveryCacheSettings(Path file, Duration positiveTtl, Duration negativeTtl) {

    public DiscoveryCacheSettings {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (positiveTtl == null || negativeTtl == null) {
            throw new IllegalArgumentException("TTLs must not be null");
        }
    }
}
