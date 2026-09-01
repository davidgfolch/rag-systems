package com.rag.common.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.repositories.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Orchestrates the ingestion pipeline: parse → split → embed → store.
 *
 * <p>Depends only on the strategy interfaces (DIP, SoC), keeping provider
 * specifics isolated in the adapter layer.
 */
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

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
        log.info("Ingested document {} -> {} chunks", document.getId(), chunks.size());

        return new IngestionResult(document.getId(), chunks.size());
    }

    public record IngestionResult(String documentId, int chunkCount) {}
}