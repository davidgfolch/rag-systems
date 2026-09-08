package com.rag.provider.domain;

import java.util.List;

/**
 * Port for fetching the model catalog from its remote source. The provider
 * module has no rag-common dependency, so the port deliberately lives in the
 * domain layer where the adapter can implement it (adapter &rarr; domain only).
 */
public interface ModelCatalogPort {

    /**
     * Fetches the full catalog. Implementations must fail fast on transport or
     * parse errors; callers decide how to handle a failed fetch.
     *
     * @return all catalog entries; empty list when the source returned no models
     */
    List<ModelInfo> fetch();
}