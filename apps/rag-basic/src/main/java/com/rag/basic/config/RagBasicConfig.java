package com.rag.basic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.basic.api.chat.ChatWebSocketHandler;
import com.rag.basic.services.RetrievalService;
import com.rag.basic.services.WebCrawlerClient;
import com.rag.common.adapter.ProviderHttpClient;
import com.rag.common.adapter.RemoteChatModelPort;
import com.rag.common.adapter.RemoteEmbeddingModel;
import com.rag.common.adapter.SpringAiEmbeddingModel;
import com.rag.common.repositories.VectorStorePort;
import com.rag.common.repositories.store.InMemoryVectorStore;
import com.rag.common.repositories.store.PgVectorStoreAdapter;
import com.rag.common.services.ChatModelPort;
import com.rag.common.services.ChatService;
import com.rag.common.services.AsyncIngestionService;
import com.rag.common.services.DocumentParser;
import com.rag.common.services.EmbeddingModelPort;
import com.rag.common.services.IngestionService;
import com.rag.common.services.TextSplitter;
import com.rag.common.services.chunking.FixedSizeChunker;
import com.rag.common.services.chunking.RecursiveCharacterChunker;
import com.rag.common.services.chunking.TokenChunker;
import com.rag.common.services.parsing.PlainTextParser;
import com.rag.common.services.parsing.TikaDocumentParser;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

/**
 * Wiring for the rag-basic module. Exposes the domain strategy interfaces so its
 * consumers can depend on abstractions, and picks concrete implementations from
 * {@code application.yml} properties. Chat + embedding models are consumed as
 * remote bridges to the rag-provider service.
 */
@Configuration
public class RagBasicConfig {

    private static final String DEFAULT_SCHEMA = "public";

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
    public DocumentParser documentParser(@Value("${rag.parsing.mode:tika}") String mode) {
        return "tika".equalsIgnoreCase(mode) ? new TikaDocumentParser() : new PlainTextParser();
    }

    @Bean
    public ProviderHttpClient providerHttpClient(
            @Value("${rag.provider.url:http://localhost:8086}") String baseUrl,
            ObjectMapper objectMapper) {
        return new ProviderHttpClient(baseUrl, objectMapper);
    }

    @Bean
    public RemoteEmbeddingModel remoteEmbeddingModel(ProviderHttpClient providerHttpClient) {
        return new RemoteEmbeddingModel(providerHttpClient);
    }

    @Bean
    public EmbeddingModelPort embeddingModel(EmbeddingModel springAiEmbeddingModel) {
        return new SpringAiEmbeddingModel(springAiEmbeddingModel);
    }

    @Bean
    @Lazy
    public PgVectorStore pgVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel springAiEmbeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:}") String schemaOverride,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName,
            @Value("${spring.ai.vectorstore.pgvector.initialize-schema:true}") boolean initializeSchema) {
        var schema = schemaOverride.isBlank()
                ? embeddingSchema(springAiEmbeddingModel)
                : schemaOverride.trim();
        return PgVectorStore.builder(jdbcTemplate, springAiEmbeddingModel)
                .schemaName(schema)
                .vectorTableName(tableName)
                .initializeSchema(initializeSchema)
                .build();
    }

    @Bean
    @Lazy
    public VectorStorePort vectorStore(
            @Value("${rag.vector-store.type:pgvector}") String type,
            EmbeddingModelPort embeddingModel,
            EmbeddingModel springAiEmbeddingModel,
            ObjectProvider<PgVectorStore> pgVectorStore,
            ObjectProvider<DataSource> dataSource,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:}") String schemaOverride,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName) {
        if ("simple".equalsIgnoreCase(type)) {
            return new InMemoryVectorStore(embeddingModel);
        }
        var schema = schemaOverride.isBlank()
                ? embeddingSchema(springAiEmbeddingModel)
                : schemaOverride.trim();
        return new PgVectorStoreAdapter(pgVectorStore.getObject(), dataSource.getIfAvailable(), schema, tableName);
    }

    /**
     * Derives the pgvector schema from the active embedding dimension so the
     * store always matches the model actually in use (a dimension switch lands
     * in a fresh schema instead of colliding with vectors of another length).
     */
    private static String embeddingSchema(EmbeddingModel model) {
        if (model instanceof RemoteEmbeddingModel remote) {
            return "rag_" + remote.dimensions();
        }
        return DEFAULT_SCHEMA;
    }

    @Bean
    public IngestionService ingestionService(DocumentParser parser, TextSplitter splitter,
                                             EmbeddingModelPort embeddingModel, VectorStorePort vectorStore) {
        return new IngestionService(parser, splitter, embeddingModel, vectorStore);
    }

    @Bean
    public AsyncIngestionService asyncIngestionService(IngestionService ingestionService, VectorStorePort vectorStore) {
        return new AsyncIngestionService(ingestionService, vectorStore, null);
    }

    @Bean
    public RetrievalService retrievalService(VectorStorePort vectorStore) {
        return new RetrievalService(vectorStore);
    }

    @Bean
    public ChatModelPort chatModel(RemoteChatModelPort remoteChatModelPort) {
        return remoteChatModelPort;
    }

    @Bean
    public RemoteChatModelPort remoteChatModelPort(ProviderHttpClient providerHttpClient, ObjectMapper objectMapper) {
        return new RemoteChatModelPort(providerHttpClient, objectMapper);
    }

    @Bean
    public ChatService chatService(VectorStorePort vectorStore, ChatModelPort chatModel) {
        return new ChatService(vectorStore, chatModel);
    }

    @Bean
    public WebCrawlerClient webCrawlerClient(
            @Value("${rag.webcrawler.url:http://localhost:8085}") String baseUrl) {
        return new WebCrawlerClient(RestClient.builder().baseUrl(baseUrl).build());
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler(
            ChatService chatService, ObjectMapper objectMapper) {
        return new ChatWebSocketHandler(chatService, objectMapper);
    }
}