package com.rag.common.repositories.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.domain.Chunk;
import com.rag.common.domain.DocumentSummary;
import com.rag.common.repositories.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.rag.common.domain.MetadataKeys.CHUNK_INDEX;
import static com.rag.common.domain.MetadataKeys.DOCUMENT_ID;

/**
 * Adapter bridging the domain {@link VectorStorePort} interface onto Spring AI's
 * {@link VectorStore} (backed by PgVector).
 * Maps our {@link Chunk} model to Spring AI documents for storage and back for
 * retrieval.
 *
 * <p>Spring AI embeds documents and queries internally via its own injected
 * embedding model, so this adapter does not handle embeddings directly.
 */
public class PgVectorStoreAdapter implements VectorStorePort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreAdapter.class);
    private static final ObjectMapper om = new ObjectMapper();

    static final String DEFAULT_SCHEMA = "public";
    static final String DEFAULT_TABLE = "vector_store";

    private final VectorStore delegate;
    private final DataSource dataSource;
    private final String table;

    public PgVectorStoreAdapter(VectorStore delegate) {
        this(delegate, null, DEFAULT_SCHEMA, DEFAULT_TABLE);
    }

    public PgVectorStoreAdapter(VectorStore delegate, DataSource dataSource) {
        this(delegate, dataSource, DEFAULT_SCHEMA, DEFAULT_TABLE);
    }

    public PgVectorStoreAdapter(VectorStore delegate, DataSource dataSource, String table) {
        this(delegate, dataSource, DEFAULT_SCHEMA, table);
    }

    public PgVectorStoreAdapter(VectorStore delegate, DataSource dataSource,
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
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                if (!rs.next()) {
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
        var docs = chunks.stream()
                .map(this::toSpringDocument)
                .toList();
        delegate.add(docs);
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK) {
        var req = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();
        var hits = delegate.similaritySearch(req).stream()
                .map(this::toChunk)
                .toList();
        log.debug("Vector store returned {} chunks for query", hits.size());
        return hits;
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK, String documentId) {
        var req = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression("documentId == '%s'".formatted(documentId))
                .build();
        var hits = delegate.similaritySearch(req).stream()
                .map(this::toChunk)
                .toList();
        log.debug("Vector store returned {} chunks for query scoped to {}", hits.size(), documentId);
        return hits;
    }

    @Override
    @SuppressWarnings("java:S2077")
    public List<DocumentSummary> listDocuments() {
        var result = new ArrayList<DocumentSummary>();
        if (dataSource == null) {
            return result;
        }
        var sql = "SELECT metadata->>'documentId' AS document_id, " +
                "count(*) AS chunk_count, " +
                "(array_agg(metadata ORDER BY metadata->>'chunkIndex'))[1] AS first_meta " +
                "FROM " + table + " " +
                "WHERE metadata->>'documentId' IS NOT NULL " +
                "GROUP BY metadata->>'documentId'";
        try (var conn = dataSource.getConnection();
             var pstmt = conn.prepareStatement(sql);
             var rs = pstmt.executeQuery()) {
            while (rs.next()) {
                var docId = rs.getString("document_id");
                var chunkCount = rs.getInt("chunk_count");
                var metadata = toMetadata(rs.getString("first_meta"));
                result.add(new DocumentSummary(docId, chunkCount, metadata));
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

    @Override
    @SuppressWarnings("java:S2077")
    public void delete(String documentId) {
        if (dataSource == null) {
            return;
        }
        var sql = "DELETE FROM " + table + " WHERE metadata->>'documentId' = ?";
        try (var conn = dataSource.getConnection();
             var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, documentId);
            int deleted = pstmt.executeUpdate();
            log.debug("Deleted {} chunks for document {} from vector store", deleted, documentId);
        } catch (SQLException e) {
            if (isMissingTable(e)) {
                return;
            }
            throw new IllegalStateException("Failed to delete document " + documentId
                    + " from vector store: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private Document toSpringDocument(Chunk chunk) {
        var metadata = new HashMap<>(chunk.getMetadata());
        metadata.put(DOCUMENT_ID, chunk.getDocumentId());
        metadata.put(CHUNK_INDEX, chunk.getIndex());
        return Document.builder()
                .id(chunk.getId())
                .text(chunk.getContent())
                .metadata(metadata)
                .build();
    }

    private Chunk toChunk(Document doc) {
        var docId = doc.getMetadata().get(DOCUMENT_ID);
        var index = doc.getMetadata().get(CHUNK_INDEX);
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
            return om.readValue(String.valueOf(raw),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            return Map.of();
        }
    }
}