package com.rag.provider.services;

import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.provider.domain.ModelCapabilities;
import com.rag.provider.domain.ModelCatalogPort;
import com.rag.provider.domain.ModelCost;
import com.rag.provider.domain.ModelInfo;
import com.rag.provider.domain.ModelLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelCatalogServiceTest {

    private static final String SOURCE = "https://models.dev/api.json";
    private static final Duration TTL = Duration.ofMinutes(60);

    @Mock private ModelCatalogPort port;
    private MutableClock clock;
    private ModelCatalogService sut;

    private static final ModelInfo PHI4 = new ModelInfo("ollama", "phi4", "Phi 4",
            new ModelLimits(16000, 0),
            new ModelCapabilities(true, false, true),
            new ModelCost(0, 0), null);

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        sut = new ModelCatalogService(port, SOURCE, TTL, true, clock);
    }

    @Test
    void shouldFetchCatalogOnFirstAccess() {
        when(port.fetch()).thenReturn(List.of(PHI4));
        ModelCatalogDTO catalog = sut.catalog();
        assertThat(catalog.models()).hasSize(1);
        assertThat(catalog.source()).isEqualTo(SOURCE);
        assertThat(catalog.fetchedAt()).isEqualTo(clock.instant());
        assertThat(catalog.models().get(0).providerId()).isEqualTo("ollama");
        assertThat(catalog.models().get(0).name()).isEqualTo("Phi 4");
        assertThat(catalog.models().get(0).limits().context()).isEqualTo(16000);
        assertThat(catalog.models().get(0).capabilities().reasoning()).isTrue();
        assertThat(catalog.models().get(0).cost().inputPerMillion()).isZero();
        verify(port, times(1)).fetch();
    }

    @Test
    void shouldReuseCacheWithinTtl() {
        when(port.fetch()).thenReturn(List.of(PHI4));
        sut.catalog();
        sut.catalog();
        verify(port, times(1)).fetch();
    }

    @Test
    void shouldRefetchWhenStale() {
        when(port.fetch()).thenReturn(List.of(PHI4));
        sut.catalog();
        clock.advance(TTL);
        sut.catalog();
        verify(port, times(2)).fetch();
    }

    @Test
    void shouldKeepPreviousCacheWhenRefreshFails() {
        when(port.fetch()).thenReturn(List.of(PHI4));
        var first = sut.catalog();
        clock.advance(TTL);
        when(port.fetch()).thenThrow(new RestClientException("network down"));
        var stale = sut.catalog();
        assertThat(stale.models()).containsExactlyElementsOf(first.models());
        assertThat(stale.fetchedAt()).isEqualTo(first.fetchedAt());
        verify(port, times(2)).fetch();
    }

    @Test
    void shouldReturnEmptyCatalogWhenDisabled() {
        sut = new ModelCatalogService(port, SOURCE, TTL, false, clock);
        assertThat(sut.catalog().models()).isEmpty();
        assertThat(sut.refresh().models()).isEmpty();
        verify(port, times(0)).fetch();
    }

    @Test
    void shouldTreatEmptyCatalogAsUnverifiable() {
        when(port.fetch()).thenReturn(List.of());
        sut.catalog();
        assertThat(sut.isKnown("ollama", "phi4")).isTrue();
    }

    @Test
    void shouldReportKnownAndUnknownModels() {
        when(port.fetch()).thenReturn(List.of(PHI4));
        sut.catalog();
        assertThat(sut.isKnown("ollama", "phi4")).isTrue();
        assertThat(sut.isKnown("ollama", "missing")).isFalse();
        assertThat(sut.isKnown("openai", "phi4")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}