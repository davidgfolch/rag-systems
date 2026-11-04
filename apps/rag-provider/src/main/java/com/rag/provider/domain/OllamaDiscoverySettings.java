package com.rag.provider.domain;

import java.time.Duration;

/**
 * Knobs for an Ollama LAN scan: whether it is enabled, which port to probe and
 * how long the TCP connect, HTTP verification and host budget may take.
 */
public record OllamaDiscoverySettings(boolean enabled, int port, Duration connectTimeout,
                                      Duration probeTimeout, int maxHosts) {

    private static final int MAX_PORT = 65_535;

    public OllamaDiscoverySettings {
        if (port <= 0 || port > MAX_PORT) {
            throw new IllegalArgumentException("port must be between 1 and " + MAX_PORT);
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (probeTimeout == null || probeTimeout.isNegative() || probeTimeout.isZero()) {
            throw new IllegalArgumentException("probeTimeout must be positive");
        }
        if (maxHosts < 0) {
            throw new IllegalArgumentException("maxHosts must not be negative");
        }
    }
}
