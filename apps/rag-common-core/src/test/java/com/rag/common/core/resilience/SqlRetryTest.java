package com.rag.common.core.resilience;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class SqlRetryTest {

    @ParameterizedTest
    @ValueSource(strings = {"08006", "08S01", "40001", "40P01", "53300", "57P03"})
    void shouldRetryTransientSqlStates(String state) {
        assertThat(SqlRetry.isTransient(state)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"23505", "23503", "42P01", "28000", "57014", "XX000"})
    void shouldNotRetryPermanentSqlStates(String state) {
        assertThat(SqlRetry.isTransient(state)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldNotRetryMissingSqlState(String state) {
        assertThat(SqlRetry.isTransient(state)).isFalse();
    }

    @Test
    void shouldDetectTransientSqlStateThroughTheCauseChain() {
        var failure = new SqlRetry.Failure(new SQLException("deadlock", "40P01"));

        assertThat(SqlRetry.isTransient(failure)).isTrue();
        assertThat(SqlRetry.isTransient(new IllegalStateException("plain"))).isFalse();
    }

    @Test
    void shouldExposeTheOriginalSqlException() {
        var sql = new SQLException("down", "08006");

        assertThat((Object) new SqlRetry.Failure(sql).sqlException()).isSameAs(sql);
    }
}
