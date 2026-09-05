package com.rag.common.repositories.store;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.DocumentSummary;
import com.rag.common.repositories.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SearchRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter bridging the domain {@link VectorStore} interface onto Spring AI's
 * {@link org.springframework.ai.vectorstore.VectorStore} (backed by PgVector).
 * Maps our {@link Chunk} model to Spring AI documents for storage and back for
 * retrieval.
 *
 * <p>Spring AI embeds documents and queries internally via its own injected
 * embedding model, so this adapter does not handle embeddings directly.
 */
public class PgVectorStoreAdapter implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreAdapter.class);

    static final String DEFAULT_SCHEMA = "public";
    static final String DEFAULT_TABLE = "vector_store";

    private final org.springframework.ai.vectorstore.VectorStore delegate;
    private final DataSource dataSource;
    private final String table;

    public PgVectorStoreAdapter(org.springframework.ai.vectorstore.VectorStore delegate) {
        this(delegate, null, DEFAULT_SCHEMA, DEFAULT_TABLE);
    }

    public PgVectorStoreAdapter(org.springframework.ai.vectorstore.VectorStore delegate, DataSource dataSource) {
        this(delegate, dataSource, DEFAULT_SCHEMA, DEFAULT_TABLE);
    }

    public PgVectorStoreAdapter(org.springframework.ai.vectorstore.VectorStore delegate, DataSource dataSource, String table) {
        this(delegate, dataSource, DEFAULT_SCHEMA, table);
    }

    public PgVectorStoreAdapter(org.springframework.ai.vectorstore.VectorStore delegate, DataSource dataSource,
                                String schema, String table) {
        this.delegate = delegate;
        this.dataSource = dataSource;
        this.table = qualified(schema, table);
    }

    /**
     * Builds a quoted, schema-qualified identifier so the table resolves to the
     * exact location Spring AI writes to (e.g. "rag_basic"."chunks") regardless
     * of the connection's search_path.
     */
    private static String qualified(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    private static String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "";
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void checkAvailable() {
        if (dataSource == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT 1")) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Vector store did not respond to connectivity probe");
                }
            }
            log.debug("Vector store connectivity probe succeeded");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Vector store not available: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void add(List<Chunk> chunks) {
        log.debug("Adding {} chunks to vector store {}", chunks.size(), table);
        List<org.springframework.ai.document.Document> docs = chunks.stream()
                .map(this::toSpringDocument)
                .toList();
        delegate.add(docs);
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        List<Chunk> hits = delegate.similaritySearch(request).stream()
                .map(this::toChunk)
                .toList();
        log.debug("Vector store returned {} chunks for query", hits.size());
        return hits;
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK, String documentId) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("documentId == '%s'".formatted(documentId))
                .build();
        List<Chunk> hits = delegate.similaritySearch(request).stream()
                .map(this::toChunk)
                .toList();
        log.debug("Vector store returned {} chunks for query scoped to {}", hits.size(), documentId);
        return hits;
    }

    @Override
    @SuppressWarnings("java:S2077")
    public List<DocumentSummary> listDocuments() {
        List<DocumentSummary> result = new ArrayList<>();
        if (dataSource == null) {
            return result;
        }
        String sql = "SELECT metadata->>'documentId' AS document_id, " +
                "count(*) AS chunk_count, " +
                "(array_agg(metadata ORDER BY metadata->>'chunkIndex'))[1] AS first_meta " +
                "FROM " + table + " " +
                "WHERE metadata->>'documentId' IS NOT NULL " +
                "GROUP BY metadata->>'documentId'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String documentId = rs.getString("document_id");
                int chunkCount = rs.getInt("chunk_count");
                Map<String, Object> metadata = toMetadata(rs.getString("first_meta"));
                result.add(new DocumentSummary(documentId, chunkCount, metadata));
            }
        } catch (SQLException e) {
            if (isMissingTable(e)) {
                return List.of();
            }
            throw new IllegalStateException("Failed to list documents from vector store: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
        log.debug("Listed {} documents from vector store", result.size());
        return result;
    }

    /**
     * The vector store table may not exist if nothing has been ingested yet.
     * Treat that as "no documents" rather than a fatal error so the TUI can
     * report an empty list instead of crashing (PostgreSQL error 42P01).
     */
    private static boolean isMissingTable(SQLException e) {
        return "42P01".equals(e.getSQLState());
    }

    private org.springframework.ai.document.Document toSpringDocument(Chunk chunk) {
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
        metadata.put("documentId", chunk.getDocumentId());
        metadata.put("chunkIndex", chunk.getIndex());
        return org.springframework.ai.document.Document.builder()
                .id(chunk.getId())
                .text(chunk.getContent())
                .metadata(metadata)
                .build();
    }

    private Chunk toChunk(org.springframework.ai.document.Document doc) {
        Object docId = doc.getMetadata().get("documentId");
        Object index = doc.getMetadata().get("chunkIndex");
        return new Chunk(
                doc.getId(),
                docId == null ? "unknown" : String.valueOf(docId),
                doc.getText(),
                index == null ? 0 : Integer.parseInt(String.valueOf(index)),
                doc.getMetadata()
        );
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
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(String.valueOf(raw),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}