package com.rag.common.retrieval.store;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.resilience.RetryPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorWriterRetryTest {

    private static final Duration FAST = Duration.ofMillis(1);
    private static final RetryPolicy FAST_RETRY = new RetryPolicy(3, FAST, FAST, 2.0, 0);
    private static final String TABLE = "\"s\".\"t\"";

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private final Statement indexStatement = mock(Statement.class);

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.createStatement()).thenReturn(indexStatement);
    }

    @Test
    void shouldRetryTransientSqlStateThenSucceed() throws Exception {
        when(statement.executeBatch())
                .thenThrow(sqlException("08006"))
                .thenReturn(new int[]{1});
        var sut = writer();

        sut.add(List.of(chunk()));

        verify(statement, times(2)).executeBatch();
        verify(statement, times(2)).addBatch();
    }

    @Test
    void shouldGiveUpAfterTransientFailuresAreExhausted() throws Exception {
        when(statement.executeBatch()).thenThrow(sqlException("40P01"));
        var sut = writer();
        var batch = List.of(chunk());

        assertThatThrownBy(() -> sut.add(batch))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to write 1 chunks to " + TABLE)
                .hasMessageContaining("40P01");
        verify(statement, times(3)).executeBatch();
    }

    @Test
    void shouldNotRetryIntegrityViolation() throws Exception {
        when(statement.executeBatch()).thenThrow(sqlException("23505"));
        var sut = writer();
        var batch = List.of(chunk());

        assertThatThrownBy(() -> sut.add(batch))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to write 1 chunks to " + TABLE);
        verify(statement, times(1)).executeBatch();
    }

    @Test
    void shouldNotRetryMissingTable() throws Exception {
        when(statement.executeBatch()).thenThrow(sqlException("42P01"));
        var sut = writer();
        var batch = List.of(chunk());

        assertThatThrownBy(() -> sut.add(batch))
                .isInstanceOf(IllegalStateException.class);
        verify(statement, times(1)).executeBatch();
    }

    @Test
    void shouldRetryIndexCreationOnTransientFailure() throws Exception {
        when(indexStatement.execute(contains("CREATE INDEX IF NOT EXISTS")))
                .thenThrow(sqlException("08006"))
                .thenReturn(true);
        when(statement.executeBatch()).thenReturn(new int[]{1});
        var sut = writer();

        sut.add(List.of(chunk()));

        verify(indexStatement, times(2)).execute(contains("CREATE INDEX IF NOT EXISTS"));
    }

    @Test
    void shouldApplyQueryTimeoutToEveryStatement() throws Exception {
        when(statement.executeBatch()).thenReturn(new int[]{1});
        var sut = writer();

        sut.add(List.of(chunk()));

        ArgumentCaptor<Integer> captor = ArgumentCaptor.captor();
        verify(statement).setQueryTimeout(captor.capture());
        verify(indexStatement).setQueryTimeout(captor.capture());
        assertThat(captor.getAllValues()).containsOnly(30);
    }

    private PgVectorWriter writer() {
        return new PgVectorWriter(dataSource, TABLE, "idx", FAST_RETRY, 30);
    }

    private static SQLException sqlException(String sqlState) {
        return new SQLException("failure " + sqlState, sqlState);
    }

    private static Chunk chunk() {
        var chunk = new Chunk(UUID.randomUUID().toString(), "d1", "text", 0, Map.of());
        chunk.setEmbedding(new float[]{1f, 0f});
        return chunk;
    }
}
