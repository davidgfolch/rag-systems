package com.rag.tui.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.repositories.VectorStore;
import com.rag.common.services.DocumentParser;
import com.rag.common.services.EmbeddingModel;
import com.rag.common.services.TextSplitter;

import java.util.List;

/**
 * Orchestrates the ingestion pipeline: parse → split → embed → store.
 * Depends only on the {@code rag-common} strategy interfaces.
 */
public class IngestionService {

    private final DocumentParser parser;
    private final TextSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    public IngestionService(DocumentParser parser, TextSplitter splitter,
                            EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this.parser = parser;
        this.splitter = splitter;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    public IngestionResult ingest(Document document) {
        String parsed = parser.parse(document);
        Document clean = parsed.equals(document.getContent())
                ? document
                : new Document(document.getId(), parsed, document.getMetadata());

        List<Chunk> chunks = splitter.split(clean);
        for (Chunk chunk : chunks) {
            chunk.setEmbedding(embeddingModel.embed(chunk.getContent()));
        }
        vectorStore.add(chunks);

        return new IngestionResult(document.getId(), chunks.size());
    }

    public record IngestionResult(String documentId, int chunkCount) {}
}
