package com.rag.common.services;

import com.rag.common.domain.Chunk;
import com.rag.common.domain.Document;
import com.rag.common.repositories.VectorStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.IntStream;

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
    private final EmbeddingModelPort embeddingModel;
    private final VectorStorePort vectorStore;

    public IngestionService(DocumentParser parser, TextSplitter splitter,
                            EmbeddingModelPort embeddingModel, VectorStorePort vectorStore) {
        this.parser = parser;
        this.splitter = splitter;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
    }

    public IngestionResult ingest(Document doc) {
        log.info("Ingestion start: document {} (content chars={}, metadata keys={})",
                doc.getId(), doc.getContent().length(), doc.getMetadata().keySet());
        var chunks = getChunks(doc);
        vectorStore.add(chunks);
        log.info("Ingested document {} -> {} chunks", doc.getId(), chunks.size());
        return new IngestionResult(doc.getId(), chunks.size());
    }

    private List<Chunk> getChunks(Document doc) {
        String parsed = parser.parse(doc);
        log.info("Ingestion {} parsed: {} characters extracted", doc.getId(), parsed.length());
        validateParseOrThrow(doc, parsed);
        var cleanDoc = parsed.equals(doc.getContent()) ? doc : new Document(doc.getId(), parsed, doc.getMetadata());
        var chunks = splitter.split(cleanDoc);
        log.info("Ingestion {} split: {} chunks", doc.getId(), chunks.size());
        embedChunks(chunks);
        log.info("Ingestion {} embedded: {} chunks", doc.getId(), chunks.size());
        return chunks;
    }

    private void embedChunks(List<Chunk> chunks) {
        var texts = chunks.stream().map(Chunk::getContent).toList();
        var vectors = embeddingModel.embed(texts);
        IntStream.range(0, chunks.size())
                .forEach(i -> chunks.get(i).setEmbedding(vectors.get(i)));
    }

    public record IngestionResult(String documentId, int chunkCount) {}

    private void validateParseOrThrow(Document doc, String parsed) {
        if (parsed.isBlank()) {
            log.warn("Ingestion {} aborted: extracted text is empty or blank", doc.getId());
            throw new EmptyExtractionException(doc);
        }
    }

    public static class EmptyExtractionException extends RuntimeException {
        public static final String MSG = "No text could be extracted from document ";
        public EmptyExtractionException(Document doc) {
            super(MSG + doc.getId());
        }
    }
}