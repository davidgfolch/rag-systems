package com.rag.common.retrieval.store;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.DocumentSummary;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.resilience.RetryObserver;
import com.rag.common.core.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;

import static com.rag.common.core.domain.MetadataKeys.CHUNK_INDEX;
import static com.rag.common.core.domain.MetadataKeys.DOCUMENT_ID;

/**
 * Adapter bridging the domain {@link VectorStorePort} interface onto Spring AI's
 * {@link VectorStore} (backed by PgVector).
 * Maps our {@link Chunk} model to Spring AI documents for storage and back for
 * retrieval.
 *
 * <p>Retrieval is delegated to Spring AI, which embeds the query and searches
 * the same table. Writes persist the embeddings already computed by the
 * ingestion pipeline (see {@link PgVectorWriter}); only stores built without a
 * {@link DataSource} fall back to Spring AI's add, which re-embeds documents.
 *
 * <p>Idempotent JDBC statements (delete, list, connectivity probe) are retried
 * on transient connection/transaction failures by {@link PgVectorJdbc}.
 */
public class PgVectorStoreAdapter implements VectorStorePort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorStoreAdapter.class);

    static final String DEFAULT_SCHEMA = "public";
    static final String DEFAULT_TABLE = "chunks";
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final VectorStore delegate;
    private final String table;
    private final PgVectorWriter writer;
    private final PgVectorJdbc jdbc;

    public PgVectorStoreAdapter(VectorStore delegate) {
        this(delegate, null, DEFAULT_SCHEMA, DEFAULT_TABLE, RetryPolicy.defaults());
    }

    public PgVectorStoreAdapter(VectorStore delegate, DataSource dataSource) {
        this(delegate, dataSource, DEFAULT_SCHEMA, DEFAULT_TABLE, RetryPolicy.defaults());
    }

    public PgVectorStoreAdapter(VectorStore delegate, DataSource dataSource, String table) {
        this(delegate, dataSource, DEFAULT_SCHEMA, table, RetryPolicy.defaults());
    }

    public PgVectorStoreAdapter(VectorStore delegate, DataSource dataSource,
                                String schema, String table) {
        this(delegate, dataSource, schema, table, RetryPolicy.defaults());
    }

    public PgVectorStoreAdapter(VectorStore delegate, DataSource dataSource,
                                String schema, String table, RetryPolicy retryPolicy) {
        this(delegate, dataSource, schema, table, retryPolicy, RetryObserver.NOOP);
    }

    public PgVectorStoreAdapter(VectorStore delegate, DataSource dataSource,
                                String schema, String table, RetryPolicy retryPolicy,
                                RetryObserver observer) {
        this.delegate = delegate;
        this.table = qualified(schema, table);
        this.writer = dataSource == null ? null
                : new PgVectorWriter(dataSource, this.table, PgVectorWriter.indexName(schema, table),
                retryPolicy, QUERY_TIMEOUT_SECONDS, observer);
        this.jdbc = dataSource == null ? null
                : new PgVectorJdbc(dataSource, this.table, retryPolicy, QUERY_TIMEOUT_SECONDS, observer);
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
        if (jdbc != null) {
            jdbc.checkAvailable();
        }
    }

    @Override
    public void add(List<Chunk> chunks) {
        log.debug("Adding {} chunks to vector store {}", chunks.size(), table);
        if (writer != null) {
            writer.add(chunks);
            return;
        }
        var docs = chunks.stream()
                .map(this::toSpringDocument)
                .toList();
        delegate.add(docs);
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK) {
        return search(query, topK, null);
    }

    @Override
    public List<Chunk> similaritySearch(String query, int topK, String documentId) {
        return search(query, topK, documentId);
    }

    private List<Chunk> search(String query, int topK, String documentId) {
        var request = SearchRequest.builder()
                .query(query)
                .topK(ContentDeduplicator.fetchSize(topK));
        if (documentId != null) {
            request.filterExpression("documentId == '%s'".formatted(documentId));
        }
        var hits = delegate.similaritySearch(request.build()).stream()
                .map(this::toChunk)
                .toList();
        var distinct = ContentDeduplicator.distinct(hits, topK);
        log.debug("Vector store returned {} chunks ({} distinct) for query scoped to {}",
                hits.size(), distinct.size(), documentId);
        return distinct;
    }

    @Override
    public List<DocumentSummary> listDocuments() {
        return jdbc == null ? List.of() : jdbc.listDocuments();
    }

    @Override
    public void delete(String documentId) {
        if (jdbc != null) {
            jdbc.delete(documentId);
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
}
