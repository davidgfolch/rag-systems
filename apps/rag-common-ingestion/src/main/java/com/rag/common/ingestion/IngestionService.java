package com.rag.common.ingestion;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.services.ChunkingConfig;
import com.rag.common.core.services.DocumentChunker;
import com.rag.common.core.services.DocumentParser;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.core.services.TextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Orchestrates the ingestion pipeline: parse → chunk → embed → store.
 *
 * <p>Depends only on the strategy interfaces (DIP, SoC), keeping provider
 * specifics isolated in the adapter layer.
 */
public class IngestionService {

    public static final int DEFAULT_EMBED_BATCH_SIZE = 32;

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentParser parser;
    private final TextSplitter splitter;
    private final DocumentChunker documentChunker;
    private final EmbeddingModelPort embeddingModel;
    private final VectorStorePort vectorStore;
    private final int maxEmbeddingBatchSize;

    public IngestionService(DocumentParser parser, TextSplitter splitter,
                            EmbeddingModelPort embeddingModel, VectorStorePort vectorStore,
                            int maxEmbeddingBatchSize) {
        this(parser, splitter, null, embeddingModel, vectorStore, maxEmbeddingBatchSize);
    }

    public IngestionService(DocumentParser parser, DocumentChunker documentChunker,
                            EmbeddingModelPort embeddingModel, VectorStorePort vectorStore,
                            int maxEmbeddingBatchSize) {
        this(parser, null, documentChunker, embeddingModel, vectorStore, maxEmbeddingBatchSize);
    }

    private IngestionService(DocumentParser parser, TextSplitter splitter,
                             DocumentChunker documentChunker, EmbeddingModelPort embeddingModel,
                             VectorStorePort vectorStore, int maxEmbeddingBatchSize) {
        if (splitter == null && documentChunker == null) {
            throw new IllegalArgumentException("a splitter or document chunker is required");
        }
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.splitter = splitter;
        this.documentChunker = documentChunker;
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
        this.maxEmbeddingBatchSize = requirePositive(maxEmbeddingBatchSize);
    }

    public IngestionResult ingest(Document doc) {
        log.info("Ingestion start: document {} (content chars={}, metadata keys={})",
                doc.getId(), doc.getContent().length(), doc.getMetadata().keySet());
        return process(parser.parse(doc), doc);
    }

    /** Ingests an already-parsed document, preserving its structure end to end. */
    public IngestionResult ingestParsed(ParsedDocument doc) {
        log.info("Ingestion start: document {} (format={}, blocks={})",
                doc.documentId(), doc.sourceType(), doc.blocks().size());
        return process(doc, null);
    }

    private IngestionResult process(ParsedDocument doc, Document source) {
        var text = doc.text();
        log.info("Ingestion {} parsed: {} characters extracted", doc.documentId(), text.length());
        validateParseOrThrow(doc.documentId(), text);
        var chunks = chunk(doc, source);
        log.info("Ingestion {} split: {} chunks", doc.documentId(), chunks.size());
        embedChunks(chunks);
        log.info("Ingestion {} embedded: {} chunks", doc.documentId(), chunks.size());
        vectorStore.add(chunks);
        log.info("Ingested document {} -> {} chunks", doc.documentId(), chunks.size());
        return new IngestionResult(doc.documentId(), chunks.size());
    }

    private List<Chunk> chunk(ParsedDocument doc, Document source) {
        if (documentChunker != null) {
            return documentChunker.chunk(doc, ChunkingConfig.DEFAULT);
        }
        var text = doc.text();
        var clean = source != null && text.equals(source.getContent())
                ? source : new Document(doc.documentId(), text, doc.metadata());
        return splitter.split(clean);
    }

    private void embedChunks(List<Chunk> chunks) {
        for (int from = 0; from < chunks.size(); from += maxEmbeddingBatchSize) {
            var window = chunks.subList(from, Math.min(chunks.size(), from + maxEmbeddingBatchSize));
            embedChunkWindow(window);
        }
    }

    private void embedChunkWindow(List<Chunk> window) {
        log.debug("Embedding window of {} chunks", window.size());
        var texts = window.stream().map(Chunk::getContent).toList();
        var vectors = embeddingModel.embed(texts);
        IntStream.range(0, window.size())
                .forEach(i -> window.get(i).setEmbedding(vectors.get(i)));
    }

    private static int requirePositive(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("embedding batch size must be positive");
        }
        return value;
    }

    public record IngestionResult(String documentId, int chunkCount) {}

    private void validateParseOrThrow(String documentId, String parsed) {
        if (parsed.isBlank()) {
            log.warn("Ingestion {} aborted: extracted text is empty or blank", documentId);
            throw new EmptyExtractionException(documentId);
        }
    }

    public static class EmptyExtractionException extends RuntimeException {
        public static final String MSG = "No text could be extracted from document ";
        public EmptyExtractionException(String documentId) {
            super(MSG + documentId);
        }
    }
}
