package com.rag.common.retrieval.store;

import com.rag.common.core.resilience.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.VectorStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorStoreAdapterRetryTest {

    private static final Duration FAST = Duration.ofMillis(1);
    private static final RetryPolicy FAST_RETRY = new RetryPolicy(3, FAST, FAST, 2.0, 0);
    private static final int EXPECTED_QUERY_TIMEOUT = 30;

    private final VectorStore delegate = mock(VectorStore.class);

    @Test
    void shouldRetryTransientDeleteThenSucceed() throws Exception {
        var conn = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate())
                .thenThrow(sqlException("08006"))
                .thenReturn(1);
        var sut = adapter(conn);

        sut.delete("d1");

        verify(statement, times(2)).executeUpdate();
    }

    @Test
    void shouldNotRetryDeleteOnIntegrityViolation() throws Exception {
        var conn = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenThrow(sqlException("23503"));
        var sut = adapter(conn);

        assertThatThrownBy(() -> sut.delete("d1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to delete document d1 from vector store")
                .hasMessageContaining("23503");
        verify(statement, times(1)).executeUpdate();
    }

    @Test
    void shouldRetryTransientListDocumentsThenSucceed() throws Exception {
        var conn = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery())
                .thenThrow(sqlException("40P01"))
                .thenReturn(rs);
        when(rs.next()).thenReturn(false);
        var sut = adapter(conn);

        assertThat(sut.listDocuments()).isEmpty();
        verify(statement, times(2)).executeQuery();
    }

    @Test
    void shouldNotRetryListDocumentsOnAuthorizationFailure() throws Exception {
        var conn = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(sqlException("28000"));
        var sut = adapter(conn);

        assertThatThrownBy(sut::listDocuments)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to list documents from vector store");
        verify(statement, times(1)).executeQuery();
    }

    @Test
    void shouldRetryTransientConnectivityProbeThenSucceed() throws Exception {
        var conn = mock(Connection.class);
        var statement = mock(Statement.class);
        var rs = mock(ResultSet.class);
        when(conn.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1"))
                .thenThrow(sqlException("08006"))
                .thenReturn(rs);
        when(rs.next()).thenReturn(true);
        var sut = adapter(conn);

        assertThatCode(sut::checkAvailable).doesNotThrowAnyException();
        verify(statement, times(2)).executeQuery("SELECT 1");
    }

    @Test
    void shouldNotRetryConnectivityProbeOnAuthorizationFailure() throws Exception {
        var conn = mock(Connection.class);
        var statement = mock(Statement.class);
        when(conn.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenThrow(sqlException("28000"));
        var sut = adapter(conn);

        assertThatThrownBy(sut::checkAvailable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vector store not available");
        verify(statement, times(1)).executeQuery("SELECT 1");
    }

    @Test
    void shouldApplyQueryTimeoutToReadAndDeleteStatements() throws Exception {
        var conn = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rs);
        when(statement.executeUpdate()).thenReturn(1);
        when(rs.next()).thenReturn(false);
        var sut = adapter(conn);

        sut.listDocuments();
        sut.delete("d1");

        var captor = ArgumentCaptor.forClass(Integer.class);
        verify(statement, times(2)).setQueryTimeout(captor.capture());
        assertThat(captor.getAllValues()).containsOnly(EXPECTED_QUERY_TIMEOUT);
    }

    private PgVectorStoreAdapter adapter(Connection conn) throws Exception {
        var ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(conn);
        return new PgVectorStoreAdapter(delegate, ds, "rag_basic", "chunks", FAST_RETRY);
    }

    private static SQLException sqlException(String sqlState) {
        return new SQLException("failure " + sqlState, sqlState);
    }
}
