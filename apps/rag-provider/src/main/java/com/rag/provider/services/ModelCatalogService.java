package com.rag.provider.services;

import com.rag.contract.provider.ModelCapabilitiesDTO;
import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelCostDTO;
import com.rag.contract.provider.ModelLimitsDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.provider.domain.ModelCatalogPort;
import com.rag.provider.domain.ModelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Cached, offline-safe access to the model catalog. Data is fetched lazily and
 * refreshed when it is older than the TTL or on demand; a failed refresh keeps
 * the previous snapshot (or empty state) instead of throwing, so the service
 * never couples availability to the upstream source.
 *
 * <p>Reads never take the lock. A stale snapshot triggers one single-flight
 * refresh on a virtual thread: concurrent callers await the same fetch instead
 * of each holding the monitor across an HTTP call.
 *
 * <p>{@link #isKnown} backs the advisory switch check: an empty catalog states
 * "unknown/unverifiable" (returns true) so switching stays uninhibited when the
 * catalog is unreachable, e.g. for local-only models not listed in models.dev.
 */
public class ModelCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ModelCatalogService.class);

    private final ModelCatalogPort port;
    private final String source;
    private final Duration ttl;
    private final boolean enabled;
    private final Clock clock;
    @SuppressWarnings("java:S3077") // only ever replaced with an immutable snapshot
    private volatile List<ModelInfo> cache = List.of();
    private volatile Instant lastFetched;
    @SuppressWarnings("java:S3077") // CompletableFuture is thread-safe; the reference is swapped atomically
    private volatile CompletableFuture<Void> refreshInFlight;

    public ModelCatalogService(ModelCatalogPort port, String source, Duration ttl,
                               boolean enabled, Clock clock) {
        this.port = port;
        this.source = source;
        this.ttl = ttl;
        this.enabled = enabled;
        this.clock = clock;
    }

    public ModelCatalogDTO catalog() {
        if (enabled && stale()) {
            awaitRefresh();
        }
        return toDto();
    }

    public ModelCatalogDTO refresh() {
        if (enabled) {
            awaitRefresh();
        }
        return toDto();
    }

    public boolean isKnown(String providerId, String modelId) {
        var snapshot = cache;
        if (!enabled || snapshot.isEmpty()) {
            return true;
        }
        return snapshot.stream().anyMatch(m ->
                m.providerId().equals(providerId) && m.modelId().equals(modelId));
    }

    private boolean stale() {
        var fetched = lastFetched;
        return fetched == null
                || Duration.between(fetched, clock.instant()).compareTo(ttl) >= 0;
    }

    private void awaitRefresh() {
        var inFlight = refreshInFlight;
        if (inFlight == null) {
            synchronized (this) {
                if (refreshInFlight == null) {
                    refreshInFlight = CompletableFuture.runAsync(this::fetchAndStore,
                            command -> Thread.ofVirtual().start(command));
                }
                inFlight = refreshInFlight;
            }
        }
        inFlight.join();
        synchronized (this) {
            if (refreshInFlight == inFlight) {
                refreshInFlight = null;
            }
        }
    }

    private void fetchAndStore() {
        try {
            cache = List.copyOf(port.fetch());
            lastFetched = clock.instant();
            log.info("Model catalog refreshed from {} ({} models)", source, cache.size());
        } catch (RuntimeException e) {
            log.warn("Model catalog refresh failed for {}: {} (keeping cached data)",
                    source, e.getMessage());
        }
    }

    private ModelCatalogDTO toDto() {
        var snapshot = cache;
        return new ModelCatalogDTO(snapshot.stream().map(ModelCatalogService::toDto).toList(),
                source, lastFetched);
    }

    private static ProviderModelDTO toDto(ModelInfo m) {
        var limits = new ModelLimitsDTO(m.limits().context(), m.limits().output());
        var capabilities = new ModelCapabilitiesDTO(
                m.capabilities().reasoning(),
                m.capabilities().toolCall(),
                m.capabilities().structuredOutput());
        var cost = new ModelCostDTO(m.cost().inputPerMillion(), m.cost().outputPerMillion());
        return new ProviderModelDTO(m.providerId(), m.modelId(), m.name(),
                limits, capabilities, cost, m.status(), m.baseUrl(), m.apiKeyEnv());
    }
}
