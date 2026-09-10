package com.rag.provider.domain;

import java.util.List;

/**
 * Persists configured provider profiles so runtime-added providers (and their
 * API keys) survive restarts. Implementations are best-effort: load/save
 * failures are logged, never fatal.
 */
public interface ProviderProfileStore {

    ProviderProfileStore NOOP = new ProviderProfileStore() {
        @Override
        public List<ProviderProfile> load() {
            return List.of();
        }

        @Override
        public void save(List<ProviderProfile> profiles) {
            // no-op: NOOP store does not persist
        }
    };

    List<ProviderProfile> load();

    void save(List<ProviderProfile> profiles);
}