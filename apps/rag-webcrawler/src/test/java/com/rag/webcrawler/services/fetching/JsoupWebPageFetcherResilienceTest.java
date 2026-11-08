package com.rag.webcrawler.services.fetching;

import com.rag.common.core.resilience.RetryPolicy;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsoupWebPageFetcherResilienceTest {

    private static final String URL = "https://example.com/page";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Duration FAST = Duration.ofMillis(1);
    private static final RetryPolicy FAST_RETRY = new RetryPolicy(3, FAST, FAST, 2.0, 0);

    @Test
    void shouldRetryTransientTimeoutThenSucceed() throws Exception {
        var connection = mock(Connection.class);
        when(connection.get()).thenThrow(new SocketTimeoutException("timeout"))
                .thenReturn(Jsoup.parse("<html><head><title>Retried</title></head><body>ok</body></html>", URL));
        var sut = new JsoupWebPageFetcher(connection, TIMEOUT, FAST_RETRY);

        var page = sut.fetch(URL);

        assertThat(page.getTitle()).isEqualTo("Retried");
        verify(connection).timeout((int) TIMEOUT.toMillis());
        verify(connection, times(2)).get();
    }

    @Test
    void shouldRetryTransientHttpStatus() throws Exception {
        var connection = mock(Connection.class);
        when(connection.get()).thenThrow(new HttpStatusException("busy", 503, URL))
                .thenReturn(Jsoup.parse("<html><head><title>AfterRetry</title></head></html>", URL));
        var sut = new JsoupWebPageFetcher(connection, TIMEOUT, FAST_RETRY);

        assertThat(sut.fetch(URL).getTitle()).isEqualTo("AfterRetry");
        verify(connection, times(2)).get();
    }

    @Test
    void shouldNotRetryPermanentHttpStatus() throws Exception {
        var connection = mock(Connection.class);
        when(connection.get()).thenThrow(new HttpStatusException("missing", 404, URL));
        var sut = new JsoupWebPageFetcher(connection, TIMEOUT, FAST_RETRY);

        assertThatThrownBy(() -> sut.fetch(URL))
                .isInstanceOf(JsoupWebPageFetcher.WebFetchException.class)
                .hasMessage("Failed to fetch URL: " + URL);
        verify(connection, times(1)).get();
    }

    @Test
    void shouldClassifyOnlyTransientFailures() {
        assertThat(JsoupWebPageFetcher.isRetryable(
                new JsoupWebPageFetcher.WebFetchException("x", new ConnectException("refused")))).isTrue();
        assertThat(JsoupWebPageFetcher.isRetryable(
                new JsoupWebPageFetcher.WebFetchException("x", new UnknownHostException("dns")))).isTrue();
        assertThat(JsoupWebPageFetcher.isRetryable(new IllegalArgumentException("bad"))).isFalse();
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        var policy = RetryPolicy.noRetry();
        assertThatThrownBy(() -> new JsoupWebPageFetcher((Duration) null, policy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
        assertThatThrownBy(() -> new JsoupWebPageFetcher(Duration.ZERO, policy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }
}
