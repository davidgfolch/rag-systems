package com.rag.basic.config;

import com.rag.basic.services.RetrievalService;
import com.rag.basic.services.WebCrawlerClient;
import com.rag.common.adapter.SpringAiChatModel;
import com.rag.common.adapter.SpringAiEmbeddingModel;
import com.rag.common.repositories.VectorStore;
import com.rag.common.repositories.store.InMemoryVectorStore;
import com.rag.common.repositories.store.PgVectorStoreAdapter;
import com.rag.common.services.ChatModel;
import com.rag.common.services.ChatService;
import com.rag.common.services.DocumentParser;
import com.rag.common.services.EmbeddingModel;
import com.rag.common.services.IngestionService;
import com.rag.common.services.TextSplitter;
import com.rag.common.services.chunking.FixedSizeChunker;
import com.rag.common.services.chunking.RecursiveCharacterChunker;
import com.rag.common.services.chunking.TokenChunker;
import com.rag.common.services.parsing.PlainTextParser;
import com.rag.common.services.parsing.TikaDocumentParser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

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
            return new InMemoryVectorStore(embeddingModel);
        }
        if (springAiStore instanceof PgVectorStore) {
            return new PgVectorStoreAdapter(springAiStore);
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

    @Bean
    public ChatModel chatModel(ChatClient.Builder builder) {
        return new SpringAiChatModel(builder.build());
    }

    @Bean
    public ChatService chatService(VectorStore vectorStore, ChatModel chatModel) {
        return new ChatService(vectorStore, chatModel);
    }

    @Bean
    public WebCrawlerClient webCrawlerClient(
            @Value("${rag.webcrawler.url:http://localhost:8085}") String baseUrl) {
        return new WebCrawlerClient(RestClient.builder().baseUrl(baseUrl).build());
    }

    @Bean
    public com.rag.basic.api.chat.ChatWebSocketHandler chatWebSocketHandler(
            ChatService chatService, ObjectMapper objectMapper) {
        return new com.rag.basic.api.chat.ChatWebSocketHandler(chatService, objectMapper);
    }
}