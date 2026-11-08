package com.rag.common.generation.adapter;

import com.rag.common.core.resilience.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderHttpRetryTest {

    @Test
    void shouldRetryTimeoutAndConnectionFailures() {
        assertThat(ProviderHttpRetry.isRetryable(
                ProviderTransportException.failed(new HttpTimeoutException("timeout")))).isTrue();
        assertThat(ProviderHttpRetry.isRetryable(
                ProviderTransportException.failed(new ConnectException("refused")))).isTrue();
        assertThat(ProviderHttpRetry.isRetryable(
                ProviderTransportException.failed(new IOException("reset")))).isTrue();
    }

    @Test
    void shouldNotRetryInterruptedTransportFailures() {
        assertThat(ProviderHttpRetry.isRetryable(
                ProviderTransportException.interrupted(new IOException("halt")))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 425, 429, 500, 502, 503, 504})
    void shouldRetryTransientStatuses(int status) {
        assertThat(ProviderHttpRetry.isRetryable(
                new ProviderHttpException(status, "transient"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 422})
    void shouldNotRetryClientErrors(int status) {
        assertThat(ProviderHttpRetry.isRetryable(
                new ProviderHttpException(status, "permanent"))).isFalse();
    }

    @Test
    void shouldNotRetryUnrelatedFailures() {
        assertThat(ProviderHttpRetry.isRetryable(new IllegalArgumentException("bad"))).isFalse();
    }

    @Test
    void shouldExposeTheConfiguredRetryPolicy() {
        var policy = RetryPolicy.defaults();
        var settings = new ProviderHttpSettings(ProviderHttpSettings.DEFAULT_CONNECT_TIMEOUT,
                ProviderHttpSettings.DEFAULT_REQUEST_TIMEOUT, policy);

        assertThat(ProviderHttpRetry.policy(settings)).isSameAs(policy);
    }
}
