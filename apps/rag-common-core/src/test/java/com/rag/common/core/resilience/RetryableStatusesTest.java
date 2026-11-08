package com.rag.common.core.resilience;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RetryableStatusesTest {

    @ParameterizedTest
    @ValueSource(ints = {408, 425, 429, 500, 502, 503, 504, 599})
    void shouldRetryTransientStatuses(int status) {
        assertThat(RetryableStatuses.isRetryable(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 422, 600})
    void shouldNotRetryPermanentStatuses(int status) {
        assertThat(RetryableStatuses.isRetryable(status)).isFalse();
    }

    @Test
    void shouldNotTreatInformationalStatusesAsTransient() {
        assertThat(RetryableStatuses.isRetryable(100)).isFalse();
        assertThat(RetryableStatuses.isRetryable(301)).isFalse();
    }
}
