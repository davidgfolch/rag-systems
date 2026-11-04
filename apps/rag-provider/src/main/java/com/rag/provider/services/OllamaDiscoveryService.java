package com.rag.provider.services;

import com.rag.provider.adapter.DiscoveryCacheStore;
import com.rag.provider.adapter.LanScanner;
import com.rag.provider.adapter.OllamaProbe;
import com.rag.provider.domain.OllamaDiscoverySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Finds a reachable Ollama server, cheapest source first: a fresh positive
 * cache entry that still answers, the loopback interface, then one LAN scan
 * whose candidates are verified before use. A negative cache entry suppresses
 * the scan for a short TTL so repeated resolution does not hammer the network.
 */
public class OllamaDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(OllamaDiscoveryService.class);
    private static final String KIND = "ollama";
    private static final String LOOPBACK = "127.0.0.1";

    private final OllamaDiscoverySettings settings;
    private final LanScanner scanner;
    private final OllamaProbe probe;
    private final DiscoveryCacheStore cache;

    public OllamaDiscoveryService(OllamaDiscoverySettings settings, LanScanner scanner,
                                  OllamaProbe probe, DiscoveryCacheStore cache) {
        this.settings = settings;
        this.scanner = scanner;
        this.probe = probe;
        this.cache = cache;
    }

    /** The first verified Ollama host, or empty when discovery is off or found nothing. */
    public Optional<String> discover() {
        if (!settings.enabled()) {
            log.debug("Ollama discovery is disabled");
            return Optional.empty();
        }
        try {
            var entry = cache.read(KIND);
            if (entry.isPresent() && entry.get().host() == null) {
                log.info("Ollama discovery skipped: negative cache entry is still fresh");
                return Optional.empty();
            }
            if (entry.isPresent() && verifyCached(entry.get().host())) {
                return Optional.of(entry.get().host());
            }
            return probeLocal().or(this::scanLan);
        } catch (RuntimeException e) {
            log.warn("Ollama discovery failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** True when the cached host still answers; clears the entry otherwise. */
    private boolean verifyCached(String host) {
        if (probe.isAlive(host, settings.port())) {
            log.info("Ollama discovery cache hit: {}", host);
            return true;
        }
        cache.clear(KIND);
        return false;
    }

    private Optional<String> probeLocal() {
        return verify(LOOPBACK);
    }

    private Optional<String> scanLan() {
        var candidates = scanner.scan();
        for (var host : candidates) {
            var verified = verify(host);
            if (verified.isPresent()) return verified;
        }
        cache.write(KIND, null);
        log.info("Ollama LAN scan found no reachable server ({} candidates)", candidates.size());
        return Optional.empty();
    }

    private Optional<String> verify(String host) {
        if (host == null || host.isBlank() || !probe.isAlive(host, settings.port())) {
            return Optional.empty();
        }
        cache.write(KIND, host);
        log.info("Ollama host discovered at {}", host);
        return Optional.of(host);
    }
}
