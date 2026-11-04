package com.rag.provider.services;

import com.rag.provider.adapter.DiscoveryCacheStore;
import com.rag.provider.adapter.LanScanner;
import com.rag.provider.adapter.OllamaProbe;
import com.rag.provider.domain.DiscoveryCacheEntry;
import com.rag.provider.domain.OllamaDiscoverySettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaDiscoveryServiceTest {

    private static final String KIND = "ollama";
    private static final String LOOPBACK = "127.0.0.1";
    private static final int PORT = 11434;

    @Mock private LanScanner scanner;
    @Mock private OllamaProbe probe;
    @Mock private DiscoveryCacheStore cache;

    private OllamaDiscoveryService sut;

    @BeforeEach
    void setUp() {
        sut = new OllamaDiscoveryService(settings(true), scanner, probe, cache);
    }

    @Test
    void shouldSkipEverythingWhenDiscoveryIsDisabled() {
        var disabled = new OllamaDiscoveryService(settings(false), scanner, probe, cache);
        assertThat(disabled.discover()).isEmpty();
        verifyNoInteractions(scanner, probe, cache);
    }

    @Test
    void shouldReuseFreshCachedHostThatStillAnswers() {
        when(cache.read(KIND)).thenReturn(Optional.of(new DiscoveryCacheEntry("10.0.0.5", 0)));
        when(probe.isAlive("10.0.0.5", PORT)).thenReturn(true);
        assertThat(sut.discover()).contains("10.0.0.5");
        verifyNoInteractions(scanner);
        verify(cache, never()).write(anyString(), any());
    }

    @Test
    void shouldSkipScanWhenNegativeCacheEntryIsFresh() {
        when(cache.read(KIND)).thenReturn(Optional.of(new DiscoveryCacheEntry(null, 0)));
        assertThat(sut.discover()).isEmpty();
        verifyNoInteractions(scanner, probe);
        verify(cache, never()).write(anyString(), any());
    }

    @Test
    void shouldClearStaleEntryAndScanAgain() {
        when(cache.read(KIND)).thenReturn(Optional.of(new DiscoveryCacheEntry("10.0.0.5", 0)));
        when(probe.isAlive(anyString(), eq(PORT))).thenReturn(false);
        when(probe.isAlive("10.0.0.9", PORT)).thenReturn(true);
        when(scanner.scan()).thenReturn(List.of("10.0.0.9"));
        assertThat(sut.discover()).contains("10.0.0.9");
        verify(cache).clear(KIND);
        verify(cache).write(KIND, "10.0.0.9");
    }

    @Test
    void shouldPreferLoopbackBeforeScanningTheLan() {
        when(cache.read(KIND)).thenReturn(Optional.empty());
        when(probe.isAlive(LOOPBACK, PORT)).thenReturn(true);
        assertThat(sut.discover()).contains(LOOPBACK);
        verify(cache).write(KIND, LOOPBACK);
        verifyNoInteractions(scanner);
    }

    @Test
    void shouldReturnFirstVerifiedLanCandidate() {
        when(cache.read(KIND)).thenReturn(Optional.empty());
        when(probe.isAlive(anyString(), eq(PORT))).thenReturn(false);
        when(probe.isAlive("10.0.0.9", PORT)).thenReturn(true);
        when(scanner.scan()).thenReturn(List.of("10.0.0.7", "10.0.0.9"));
        assertThat(sut.discover()).contains("10.0.0.9");
        verify(cache).write(KIND, "10.0.0.9");
    }

    @Test
    void shouldRememberAnEmptyScanAsNegativeEntry() {
        when(cache.read(KIND)).thenReturn(Optional.empty());
        when(probe.isAlive(anyString(), eq(PORT))).thenReturn(false);
        when(scanner.scan()).thenReturn(List.of("10.0.0.7"));
        assertThat(sut.discover()).isEmpty();
        verify(cache).write(KIND, null);
    }

    @Test
    void shouldReturnEmptyWhenTheScannedCandidateListIsEmpty() {
        when(cache.read(KIND)).thenReturn(Optional.empty());
        when(probe.isAlive(anyString(), eq(PORT))).thenReturn(false);
        when(scanner.scan()).thenReturn(List.of());
        assertThat(sut.discover()).isEmpty();
        verify(cache).write(KIND, null);
    }

    @Test
    void shouldSwallowCacheFailures() {
        when(cache.read(KIND)).thenThrow(new IllegalStateException("boom"));
        assertThat(sut.discover()).isEmpty();
        verifyNoInteractions(scanner, probe);
    }

    @Test
    void shouldSwallowScanFailures() {
        when(cache.read(KIND)).thenReturn(Optional.empty());
        when(probe.isAlive(anyString(), eq(PORT))).thenReturn(false);
        when(scanner.scan()).thenThrow(new IllegalStateException("boom"));
        assertThat(sut.discover()).isEmpty();
        verify(cache, never()).write(anyString(), any());
    }

    private static OllamaDiscoverySettings settings(boolean enabled) {
        return new OllamaDiscoverySettings(enabled, PORT, Duration.ofMillis(100),
                Duration.ofMillis(500), 1024);
    }
}
