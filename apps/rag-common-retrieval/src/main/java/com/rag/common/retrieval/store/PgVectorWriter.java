package com.rag.common.retrieval.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import com.rag.common.core.domain.Chunk;
import com.rag.common.core.resilience.RetryExecutor;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import com.rag.common.core.resilience.SqlRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.rag.common.core.domain.MetadataKeys.CHUNK_INDEX;
import static com.rag.common.core.domain.MetadataKeys.DOCUMENT_ID;

/**
 * Writes chunks whose embeddings were already computed by the ingestion
 * pipeline. Spring AI's {@code PgVectorStore.add} re-embeds every document,
 * because its {@code Document} model has no embedding field — this writer
 * persists the vectors we already have instead of paying for a second provider
 * round trip.
 *
 * <p>Columns and upsert semantics mirror {@code PgVectorStore.doAdd} so both
 * write paths stay interchangeable. The upsert is idempotent, so transient
 * connection/transaction failures are retried with backoff.
 */
final class PgVectorWriter {

    private static final Logger log = LoggerFactory.getLogger(PgVectorWriter.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int BATCH_SIZE = 500;
    private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 30;
    private static final int MAX_IDENTIFIER_LENGTH = 63;
    private static final String DOCUMENT_INDEX_SUFFIX = "_document_id_idx";
    private static final String INSERT_SQL_TEMPLATE =
            "INSERT INTO %s (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::jsonb, ?) "
                    + "ON CONFLICT (id) DO UPDATE SET content = ? , metadata = ?::jsonb , embedding = ?";
    private static final String INDEX_SQL_TEMPLATE =
            "CREATE INDEX IF NOT EXISTS %s ON %s ((metadata->>'documentId'))";
    private static final String INSERT_FAILED = "Failed to write %d chunks to %s: %s: %s";
    private static final String INDEX_FAILED = "Could not create documentId index on %s: %s";
    private static final String WRITE_OPERATION = "write %d chunks to %s";
    private static final String INDEX_OPERATION = "create index %s on %s";

    private final DataSource dataSource;
    private final String qualifiedTable;
    private final String indexName;
    private final String insertSql;
    private final RetryExecutor retry;
    private final int queryTimeoutSeconds;
    private final AtomicBoolean indexEnsured = new AtomicBoolean();

    PgVectorWriter(DataSource dataSource, String qualifiedTable, String rawIndexName) {
        this(dataSource, qualifiedTable, rawIndexName, RetryPolicy.noRetry(), DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    PgVectorWriter(DataSource dataSource, String qualifiedTable, String rawIndexName,
                   RetryPolicy retryPolicy, int queryTimeoutSeconds) {
        this(dataSource, qualifiedTable, rawIndexName, retryPolicy, queryTimeoutSeconds, RetryObserver.NOOP);
    }

    PgVectorWriter(DataSource dataSource, String qualifiedTable, String rawIndexName,
                   RetryPolicy retryPolicy, int queryTimeoutSeconds, RetryObserver observer) {
        this.dataSource = dataSource;
        this.qualifiedTable = qualifiedTable;
        this.indexName = quote(truncate(rawIndexName));
        this.insertSql = INSERT_SQL_TEMPLATE.formatted(qualifiedTable);
        this.retry = new RetryExecutor(retryPolicy, SqlRetry::isTransient, observer, Thread::sleep);
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    void add(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        ensureDocumentIndex();
        for (int from = 0; from < chunks.size(); from += BATCH_SIZE) {
            write(chunks.subList(from, Math.min(chunks.size(), from + BATCH_SIZE)));
        }
    }

    private void write(List<Chunk> batch) {
        try {
            retry.run(WRITE_OPERATION.formatted(batch.size(), qualifiedTable), () -> executeWrite(batch));
        } catch (SqlRetry.Failure e) {
            throw failure(e.sqlException(), INSERT_FAILED.formatted(batch.size(), qualifiedTable,
                    e.sqlException().getClass().getSimpleName(), e.sqlException().getMessage()));
        }
        log.debug("Wrote {} pre-embedded chunks to {}", batch.size(), qualifiedTable);
    }

    private void executeWrite(List<Chunk> batch) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(insertSql)) {
            ps.setQueryTimeout(queryTimeoutSeconds);
            for (var chunk : batch) {
                bind(ps, chunk);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new SqlRetry.Failure(e);
        }
    }

    private static void bind(PreparedStatement ps, Chunk chunk) throws SQLException {
        var vector = chunk.getEmbedding();
        if (vector == null) {
            throw new IllegalArgumentException("Chunk " + chunk.getId() + " has no embedding");
        }
        var json = toJson(chunk);
        ps.setObject(1, UUID.fromString(chunk.getId()));
        ps.setString(2, chunk.getContent());
        ps.setString(3, json);
        ps.setObject(4, new PGvector(vector));
        ps.setString(5, chunk.getContent());
        ps.setString(6, json);
        ps.setObject(7, new PGvector(vector));
    }

    @SuppressWarnings("java:S2077") // identifiers are quoted constants, not user input
    private void ensureDocumentIndex() {
        if (!indexEnsured.compareAndSet(false, true)) {
            return;
        }
        try {
            retry.run(INDEX_OPERATION.formatted(indexName, qualifiedTable), this::executeIndex);
            log.info("Ensured documentId index {} on {}", indexName, qualifiedTable);
        } catch (SqlRetry.Failure e) {
            log.warn(INDEX_FAILED.formatted(qualifiedTable, e.sqlException().getMessage()));
        }
    }

    @SuppressWarnings("java:S2077") // identifiers are quoted constants, not user input
    private void executeIndex() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.setQueryTimeout(queryTimeoutSeconds);
            stmt.execute(INDEX_SQL_TEMPLATE.formatted(indexName, qualifiedTable));
        } catch (SQLException e) {
            throw new SqlRetry.Failure(e);
        }
    }

    private static IllegalStateException failure(SQLException sql, String message) {
        return new IllegalStateException(message, sql);
    }

    private static String toJson(Chunk chunk) {
        var metadata = new HashMap<>(chunk.getMetadata());
        metadata.put(DOCUMENT_ID, chunk.getDocumentId());
        metadata.put(CHUNK_INDEX, chunk.getIndex());
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize metadata for chunk " + chunk.getId(), e);
        }
    }

    private static String truncate(String identifier) {
        return identifier.length() <= MAX_IDENTIFIER_LENGTH
                ? identifier : identifier.substring(0, MAX_IDENTIFIER_LENGTH);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    static String indexName(String schema, String table) {
        return schema + "_" + table + DOCUMENT_INDEX_SUFFIX;
    }
}
