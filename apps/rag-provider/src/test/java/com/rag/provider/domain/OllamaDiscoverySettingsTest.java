package com.rag.provider.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaDiscoverySettingsTest {

    private static final Duration TIMEOUT = Duration.ofMillis(500);
    private static final Duration NEGATIVE_TIMEOUT = Duration.ofMillis(-1);
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);
    private static final Duration POSITIVE_TTL = Duration.ofSeconds(3600);
    private static final Duration NEGATIVE_TTL = Duration.ofSeconds(60);
    private static final Path CACHE_FILE = Path.of("cache.json");

    @Test
    void shouldKeepValidValues() {
        var settings = new OllamaDiscoverySettings(true, 11434, TIMEOUT, TIMEOUT, 1024);
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.port()).isEqualTo(11434);
        assertThat(settings.connectTimeout()).isEqualTo(TIMEOUT);
        assertThat(settings.probeTimeout()).isEqualTo(TIMEOUT);
        assertThat(settings.maxHosts()).isEqualTo(1024);
    }

    @ParameterizedTest(name = "should reject port {0}")
    @ValueSource(ints = {0, -1, 65_536})
    void shouldRejectInvalidPort(int port) {
        assertThatThrownBy(() -> new OllamaDiscoverySettings(true, port, TIMEOUT, TIMEOUT, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
    }

    @Test
    void shouldRejectInvalidTimeouts() {
        assertThatThrownBy(() -> new OllamaDiscoverySettings(true, 11434, null, TIMEOUT, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeout");
        assertThatThrownBy(() -> new OllamaDiscoverySettings(true, 11434, Duration.ZERO, TIMEOUT, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeout");
        assertThatThrownBy(() -> new OllamaDiscoverySettings(true, 11434, TIMEOUT, NEGATIVE_TIMEOUT, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probeTimeout");
        assertThatThrownBy(() -> new OllamaDiscoverySettings(true, 11434, TIMEOUT, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probeTimeout");
    }

    @Test
    void shouldRejectNegativeHostBudget() {
        assertThatThrownBy(() -> new OllamaDiscoverySettings(true, 11434, TIMEOUT, TIMEOUT, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxHosts");
    }

    @Test
    void shouldKeepValidCacheSettings() {
        var settings = new DiscoveryCacheSettings(CACHE_FILE, POSITIVE_TTL, NEGATIVE_TTL);
        assertThat(settings.file()).isEqualTo(CACHE_FILE);
        assertThat(settings.positiveTtl()).isEqualTo(POSITIVE_TTL);
        assertThat(settings.negativeTtl()).isEqualTo(NEGATIVE_TTL);
    }

    @Test
    void shouldRejectInvalidCacheSettings() {
        assertThatThrownBy(() -> new DiscoveryCacheSettings(null, ONE_SECOND, ONE_SECOND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file");
        assertThatThrownBy(() -> new DiscoveryCacheSettings(CACHE_FILE, null, ONE_SECOND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTLs");
        assertThatThrownBy(() -> new DiscoveryCacheSettings(CACHE_FILE, ONE_SECOND, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTLs");
    }
}
