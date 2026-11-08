package com.rag.provider.services;

import com.rag.common.core.resilience.ConcurrencyLimiter;
import com.rag.common.core.resilience.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceResilienceTest {

    private static final Duration SHORT_WAIT = Duration.ofMillis(20);
    private static final RetryPolicy FAST_RETRY =
            new RetryPolicy(3, Duration.ofMillis(1), Duration.ofMillis(1), 2.0, 0);

    @Mock private ModelRouter router;
    @Mock private EmbeddingModel embeddingModel;

    private EmbeddingService service(ConcurrencyLimiter limiter) {
        return new EmbeddingService(router, EmbeddingService.DEFAULT_EMBED_BATCH_SIZE, limiter, FAST_RETRY);
    }

    @Test
    void shouldRetryTransientFailureThenSucceed() {
        when(router.embeddingModelFor(null)).thenReturn(embeddingModel);
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenThrow(new ResourceAccessException("timeout", new IOException("timeout")))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[]{1f}, 0))));

        var vectors = service(new ConcurrencyLimiter("test", 1, SHORT_WAIT)).embed(List.of("a"));

        assertThat(vectors).containsExactly(List.of(1f));
        verify(embeddingModel, times(2)).call(any(EmbeddingRequest.class));
    }

    @Test
    void shouldNotRetryClientErrors() {
        when(router.embeddingModelFor(null)).thenReturn(embeddingModel);
        var failure = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad",
                HttpHeaders.EMPTY, new byte[0], null);
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> service(new ConcurrencyLimiter("test", 1, SHORT_WAIT)).embed(List.of("a")))
                .isSameAs(failure);
        verify(embeddingModel, times(1)).call(any(EmbeddingRequest.class));
    }

    @Test
    void shouldFailFastWhenSaturatedWithoutCallingTheModel() {
        var limiter = new ConcurrencyLimiter("test", 1, SHORT_WAIT);
        var service = service(limiter);
        var texts = List.of("a");
        assertThat(limiter.tryEnter()).isTrue();
        try {
            assertThatThrownBy(() -> service.embed(texts))
                    .isInstanceOf(ProviderBusyException.class)
                    .hasMessageContaining("capacity exhausted");
            verifyNoInteractions(embeddingModel);
        } finally {
            limiter.exit();
        }
    }

    @Test
    void shouldReleaseThePermitAfterAFailedCall() {
        when(router.embeddingModelFor(null)).thenReturn(embeddingModel);
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenThrow(new IllegalArgumentException("permanent"));
        var limiter = new ConcurrencyLimiter("test", 1, SHORT_WAIT);
        var service = service(limiter);
        var texts = List.of("a");

        assertThatThrownBy(() -> service.embed(texts))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(limiter.inFlight()).isZero();
        assertThat(limiter.tryEnter()).isTrue();
        limiter.exit();
    }

    @Test
    void shouldClassifyTransientFailures() {
        assertThat(EmbeddingService.isRetryable(new ResourceAccessException("timeout"))).isTrue();
        assertThat(EmbeddingService.isRetryable(HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "busy", HttpHeaders.EMPTY, new byte[0], null))).isTrue();
        assertThat(EmbeddingService.isRetryable(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "bad", HttpHeaders.EMPTY, new byte[0], null))).isFalse();
        assertThat(EmbeddingService.isRetryable(new IllegalStateException("io", new IOException("reset")))).isTrue();
        assertThat(EmbeddingService.isRetryable(new IllegalStateException("logic"))).isFalse();
    }
}
