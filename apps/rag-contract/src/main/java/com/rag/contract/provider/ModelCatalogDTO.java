package com.rag.contract.provider;

import java.time.Instant;
import java.util.List;

/**
 * Refreshable model catalog sourced from models.dev. Flat list of
 * provider/model entries plus metadata about the source and freshness.
 *
 * @param models   all known provider entries
 * @param source   catalog endpoint this data was fetched from
 * @param fetchedAt when the catalog was last fetched (null when never fetched)
 */
public record ModelCatalogDTO(List<ProviderModelDTO> models, String source, Instant fetchedAt) {
}