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
        log.info("Ingestion start: document {} (content chars={}, metadata keys={})",
                document.getId(), document.getContent().length(), document.getMetadata().keySet());

        String parsed = parser.parse(document);
        log.info("Ingestion {} parsed: {} characters extracted", document.getId(), parsed.length());
        if (shouldFailOnBlankExtraction(document, parsed)) {
            log.warn("Ingestion {} aborted: binary document yielded no extractable text", document.getId());
            throw new EmptyExtractionException(document);
        }
        Document clean = parsed.equals(document.getContent())
                ? document
                : new Document(document.getId(), parsed, document.getMetadata());

        List<Chunk> chunks = splitter.split(clean);
        log.info("Ingestion {} split: {} chunks", document.getId(), chunks.size());

        for (Chunk chunk : chunks) {
            chunk.setEmbedding(embeddingModel.embed(chunk.getContent()));
        }
        log.info("Ingestion {} embedded: {} chunks", document.getId(), chunks.size());

        vectorStore.add(chunks);
        log.info("Ingested document {} -> {} chunks", document.getId(), chunks.size());

        return new IngestionResult(document.getId(), chunks.size());
    }

    public record IngestionResult(String documentId, int chunkCount) {}

    /**
     * A binary document (carrying raw bytes for the parser) that yields no
     * text almost always means content extraction failed (e.g. a scanned/image
     * PDF with no text layer). Refuse to silently ingest zero chunks and make
     * the cause explicit instead.
     */
    private boolean shouldFailOnBlankExtraction(Document document, String parsed) {
        if (!parsed.isBlank()) {
            return false;
        }
        return document.getMetadata().containsKey("rawBytes") || document.getMetadata().containsKey("raw");
    }

    /**
     * Thrown when a binary document could not be reduced to any indexed text.
     */
    public static class EmptyExtractionException extends RuntimeException {
        public EmptyExtractionException(Document document) {
            super("No text could be extracted from binary document " + document.getId()
                    + " (it may be a scanned/image-based file without a text layer).");
        }
    }
}