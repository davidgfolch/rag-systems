package com.rag.common.generation.adapter;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterTest {

    @Test
    void shouldParseDeltaSeconds() {
        assertThat(RetryAfter.parse("7")).contains(Duration.ofSeconds(7));
    }

    @Test
    void shouldTreatANegativeDeltaAsZero() {
        assertThat(RetryAfter.parse("-3")).contains(Duration.ZERO);
    }

    @Test
    void shouldParseAnHttpDate() {
        var at = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(5);
        var header = at.format(DateTimeFormatter.RFC_1123_DATE_TIME);

        assertThat(RetryAfter.parse(header)).hasValueSatisfying(delay ->
                assertThat(delay).isBetween(Duration.ofSeconds(3), Duration.ofSeconds(6)));
    }

    @Test
    void shouldTreatAPastHttpDateAsZero() {
        var at = ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        var header = at.format(DateTimeFormatter.RFC_1123_DATE_TIME);

        assertThat(RetryAfter.parse(header)).contains(Duration.ZERO);
    }

    @Test
    void shouldReturnEmptyForMissingOrInvalidValues() {
        assertThat(RetryAfter.parse(null)).isEmpty();
        assertThat(RetryAfter.parse("  ")).isEmpty();
        assertThat(RetryAfter.parse("soon")).isEmpty();
    }
}
