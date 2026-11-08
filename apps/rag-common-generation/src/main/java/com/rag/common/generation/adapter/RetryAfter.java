package com.rag.common.generation.adapter;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses the HTTP {@code Retry-After} header, which may be either delta-seconds
 * or an HTTP-date (RFC 1123).
 */
final class RetryAfter {

    private static final Logger log = LoggerFactory.getLogger(RetryAfter.class);

    private RetryAfter() {
    }

    static Optional<Duration> parse(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        var value = header.trim();
        var seconds = parseSeconds(value);
        return seconds.isPresent() ? seconds : parseDate(value);
    }

    private static Optional<Duration> parseSeconds(String value) {
        try {
            return Optional.of(Duration.ofSeconds(Math.max(0, Long.parseLong(value))));
        } catch (NumberFormatException _) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> parseDate(String value) {
        try {
            var at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
            var delay = Duration.between(ZonedDateTime.now(at.getZone()), at);
            return Optional.of(delay.isNegative() ? Duration.ZERO : delay);
        } catch (RuntimeException e) {
            log.debug("Ignoring invalid Retry-After value '{}': {}", value, e.getMessage());
            return Optional.empty();
        }
    }
}
