package com.rag.basic.config;

import com.rag.basic.chunking.FixedSizeChunker;
import com.rag.basic.chunking.RecursiveCharacterChunker;
import com.rag.basic.chunking.TokenChunker;
import com.rag.basic.embedding.SpringAiEmbeddingModel;
import com.rag.basic.parsing.PlainTextParser;
import com.rag.basic.parsing.TikaDocumentParser;
import com.rag.basic.services.IngestionService;
import com.rag.basic.services.RetrievalService;
import com.rag.basic.vectorstore.SimpleVectorStore;
import com.rag.common.repositories.VectorStore;
import com.rag.common.services.DocumentParser;
import com.rag.common.services.EmbeddingModel;
import com.rag.common.services.TextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Wiring for the rag-basic module. Exposes the domain strategy interfaces so its
 * consumers can depend on abstractions, and picks concrete implementations from
 * {@code application.yml} properties (SoC, DIP, extensible via new strategies).
 */
@Configuration
public class RagBasicConfig {

    @Bean
    public TextSplitter textSplitter(
            @Value("${rag.chunking.strategy:recursive}") String strategy,
            @Value("${rag.chunking.size:512}") int size,
            @Value("${rag.chunking.overlap:128}") int overlap) {
        return switch (strategy.toLowerCase()) {
            case "fixed" -> new FixedSizeChunker(size, overlap);
            case "token" -> new TokenChunker(size / 4, overlap / 4);
            case "recursive", "default" -> new RecursiveCharacterChunker(size, overlap);
            default -> throw new IllegalArgumentException("Unknown chunking strategy: " + strategy);
        };
    }

    @Bean
    public DocumentParser documentParser(@Value("${rag.parsing.mode:plain}") String mode) {
        return "tika".equalsIgnoreCase(mode) ? new TikaDocumentParser() : new PlainTextParser();
    }

    @Bean
    public EmbeddingModel embeddingModel(org.springframework.ai.embedding.EmbeddingModel delegate) {
        return new SpringAiEmbeddingModel(delegate);
    }

    @Bean
    @Lazy
    public VectorStore vectorStore(
            @Value("${rag.vector-store.type:pgvector}") String type,
            EmbeddingModel embeddingModel,
            org.springframework.ai.vectorstore.VectorStore springAiStore) {
        if ("simple".equalsIgnoreCase(type)) {
            return new SimpleVectorStore(embeddingModel);
        }
        if (springAiStore instanceof PgVectorStore) {
            return new com.rag.basic.vectorstore.PgVectorStoreAdapter(springAiStore);
        }
        throw new IllegalStateException("Unsupported vector store type: " + type);
    }

    @Bean
    public IngestionService ingestionService(DocumentParser parser, TextSplitter splitter,
                                             EmbeddingModel embeddingModel, VectorStore vectorStore) {
        return new IngestionService(parser, splitter, embeddingModel, vectorStore);
    }

    @Bean
    public RetrievalService retrievalService(VectorStore vectorStore) {
        return new RetrievalService(vectorStore);
    }
}