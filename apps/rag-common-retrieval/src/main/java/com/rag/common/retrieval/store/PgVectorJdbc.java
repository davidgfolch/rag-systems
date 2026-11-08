package com.rag.common.retrieval.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.resilience.RetryExecutor;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.common.core.resilience.SqlRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDBC side of the PgVector adapter: connectivity probe, document listing and
 * document deletion. Every statement is idempotent, so transient
 * connection/transaction failures are retried with backoff, while missing-table
 * ({@code 42P01}) and integrity ({@code 23*}) errors are never retried.
 */
final class PgVectorJdbc {

    private static final Logger log = LoggerFactory.getLogger(PgVectorJdbc.class);

    private static final ObjectMapper om = new ObjectMapper();
    private static final String PROBE_SQL = "SELECT 1";
    private static final String DELETE_SQL_TEMPLATE =
            "DELETE FROM %s WHERE metadata->>'documentId' = ?";
    private static final String LIST_SQL_TEMPLATE =
            "SELECT d.document_id, d.chunk_count, first.metadata AS first_meta "
                    + "FROM (SELECT metadata->>'documentId' AS document_id, count(*) AS chunk_count "
                    + "      FROM %s "
                    + "      WHERE metadata->>'documentId' IS NOT NULL "
                    + "      GROUP BY 1) d "
                    + "JOIN LATERAL (SELECT c.metadata FROM %s c "
                    + "              WHERE c.metadata->>'documentId' = d.document_id "
                    + "              ORDER BY (c.metadata->>'chunkIndex')::int LIMIT 1) first ON true";
    private static final String NOT_AVAILABLE = "Vector store not available: %s: %s";
    private static final String LIST_FAILED = "Failed to list documents from vector store: %s: %s";
    private static final String DELETE_FAILED = "Failed to delete document %s from vector store: %s: %s";
    private static final String MISSING_TABLE = "42P01";

    private final DataSource dataSource;
    private final String table;
    private final RetryExecutor retry;
    private final int queryTimeoutSeconds;

    PgVectorJdbc(DataSource dataSource, String table, RetryPolicy retryPolicy, int queryTimeoutSeconds) {
        this.dataSource = dataSource;
        this.table = table;
        this.retry = new RetryExecutor(retryPolicy, SqlRetry::isTransient);
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    void checkAvailable() {
        try {
            retry.run("probe vector store " + table, this::executeProbe);
            log.debug("Vector store connectivity probe succeeded");
        } catch (SqlRetry.Failure e) {
            var sql = e.sqlException();
            throw failure(NOT_AVAILABLE.formatted(sql.getClass().getSimpleName(), sql.getMessage()), sql);
        } catch (RuntimeException e) {
            throw new IllegalStateException(NOT_AVAILABLE.formatted(
                    e.getClass().getSimpleName(), e.getMessage()), e);
        }
    }

    private void executeProbe() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.setQueryTimeout(queryTimeoutSeconds);
            try (var rs = stmt.executeQuery(PROBE_SQL)) {
                if (!rs.next()) {
                    throw new IllegalStateException("Vector store did not respond to connectivity probe");
                }
            }
        } catch (SQLException e) {
            throw new SqlRetry.Failure(e);
        }
    }

    List<DocumentSummary> listDocuments() {
        var sql = LIST_SQL_TEMPLATE.formatted(table, table);
        try {
            var docs = retry.call("list documents from " + table, () -> queryDocuments(sql));
            log.debug("Listed {} documents from vector store", docs.size());
            return docs;
        } catch (SqlRetry.Failure e) {
            if (isMissingTable(e.sqlException())) {
                return List.of();
            }
            var failed = e.sqlException();
            throw failure(LIST_FAILED.formatted(failed.getClass().getSimpleName(), failed.getMessage()), failed);
        }
    }

    private List<DocumentSummary> queryDocuments(String sql) {
        var result = new ArrayList<DocumentSummary>();
        try (var conn = dataSource.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setQueryTimeout(queryTimeoutSeconds);
            try (var rs = pstmt.executeQuery()) {
                collectDocuments(rs, result);
            }
        } catch (SQLException e) {
            throw new SqlRetry.Failure(e);
        }
        return result;
    }

    private static void collectDocuments(ResultSet rs, List<DocumentSummary> result) throws SQLException {
        while (rs.next()) {
            var docId = rs.getString("document_id");
            var chunkCount = rs.getInt("chunk_count");
            var metadata = toMetadata(rs.getString("first_meta"));
            result.add(new DocumentSummary(docId, chunkCount, metadata));
        }
    }

    @SuppressWarnings("java:S2077")
    void delete(String documentId) {
        var sql = DELETE_SQL_TEMPLATE.formatted(table);
        try {
            retry.run("delete document " + documentId, () -> executeDelete(sql, documentId));
        } catch (SqlRetry.Failure e) {
            if (isMissingTable(e.sqlException())) {
                return;
            }
            var failed = e.sqlException();
            throw failure(DELETE_FAILED.formatted(documentId,
                    failed.getClass().getSimpleName(), failed.getMessage()), failed);
        }
    }

    private void executeDelete(String sql, String documentId) {
        try (var conn = dataSource.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setQueryTimeout(queryTimeoutSeconds);
            pstmt.setString(1, documentId);
            int deleted = pstmt.executeUpdate();
            log.debug("Deleted {} chunks for document {} from vector store", deleted, documentId);
        } catch (SQLException e) {
            throw new SqlRetry.Failure(e);
        }
    }

    private static IllegalStateException failure(String message, SQLException sql) {
        return new IllegalStateException(message, sql);
    }

    /**
     * The vector store table may not exist if nothing has been ingested yet.
     * Treat that as "no documents" rather than a fatal error so the TUI can
     * report an empty list instead of crashing (PostgreSQL error 42P01).
     */
    private static boolean isMissingTable(SQLException e) {
        return MISSING_TABLE.equals(e.getSQLState());
    }

    /**
     * Converts a raw metadata value read from the jsonb column into a map. The
     * driver may expose it as a plain JSON string or as a PGobject.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMetadata(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        try {
            return om.readValue(String.valueOf(raw),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception _) {
            return Map.of();
        }
    }
}
