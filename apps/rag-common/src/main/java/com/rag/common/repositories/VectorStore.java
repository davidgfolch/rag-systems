package com.rag.common.repositories;

import com.rag.common.domain.Chunk;

import java.util.List;

/**
 * Strategy interface for vector storage and similarity search (Repository pattern).
 *
 * <p>Concrete stores (PgVector, SimpleVectorStore) implement this interface and
 * are swappable via configuration, keeping the retrieval layer decoupled from
 * any specific vector database.
 */
public interface VectorStore {

    /**
     * Stores the given chunks (with their embeddings) in the vector store.
     *
     * @param chunks the chunks to store
     */
    void add(List<Chunk> chunks);

    /**
     * Finds the chunks most similar to the given query.
     *
     * <p>The store is responsible for embedding the query internally, keeping
     * the caller decoupled from the embedding strategy (matching Spring AI's
     * {@code SearchRequest.query(...)} model).
     *
     * @param query the query text
     * @param topK  the number of results to return
     * @return the most similar chunks
     */
    List<Chunk> similaritySearch(String query, int topK);

    /**
     * Finds the chunks most similar to the given query, scoped to a single
     * ingested document (for per-document retrieval).
     *
     * <p>The store is responsible for embedding the query internally, keeping
     * the caller decoupled from the embedding strategy (matching Spring AI's
     * {@code SearchRequest.query(...)} model).
     *
     * @param query      the query text
     * @param topK       the number of results to return
     * @param documentId the document to scope the search to
     * @return the most similar chunks of the given document
     */
    default List<Chunk> similaritySearch(String query, int topK, String documentId) {
        return similaritySearch(query, topK).stream()
                .filter(chunk -> chunk.getDocumentId().equals(documentId))
                .toList();
    }
}