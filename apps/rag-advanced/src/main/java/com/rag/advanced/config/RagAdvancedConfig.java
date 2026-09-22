package com.rag.advanced.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.advanced.api.chat.ChatWebSocketHandler;
import com.rag.advanced.retrieval.LexicalScorer;
import com.rag.advanced.services.AdvancedRetrievalService;
import com.rag.advanced.services.WebCrawlerClient;
import com.rag.common.generation.adapter.ProviderHttpClient;
import com.rag.common.generation.adapter.RemoteChatModelPort;
import com.rag.common.generation.adapter.RemoteEmbeddingModel;
import com.rag.common.generation.adapter.SpringAiEmbeddingModel;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.retrieval.store.InMemoryVectorStore;
import com.rag.common.retrieval.store.PgVectorStoreAdapter;
import com.rag.common.core.services.ChatModelPort;
import com.rag.common.generation.ChatService;
import com.rag.common.generation.StreamingChatModelPort;
import com.rag.common.ingestion.AsyncIngestionService;
import com.rag.common.core.services.DocumentParser;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.ingestion.IngestionService;
import com.rag.common.core.services.TextSplitter;
import com.rag.common.ingestion.chunking.FixedSizeChunker;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import com.rag.common.ingestion.chunking.TokenChunker;
import com.rag.common.ingestion.parsing.PlainTextParser;
import com.rag.common.ingestion.parsing.TikaDocumentParser;
import io.micrometer.tracing.Tracer;
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
 * Wiring for the rag-advanced module. Mirrors rag-basic's strategy beans
 * (chunking/parsing/store/ingestion/chat) and adds an advanced retrieval service
 * that widens the vector pass and re-ranks candidates with a BM25-style lexical
 * pass. The pgvector schema is pinned to {@code rag_advanced} in application.yml.
 */
@Configuration
public class RagAdvancedConfig {

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
            ObjectMapper objectMapper,
            Tracer tracer) {
        return new ProviderHttpClient(baseUrl, objectMapper, tracer);
    }

    @Bean
    public RemoteEmbeddingModel remoteEmbeddingModel(
            ProviderHttpClient providerHttpClient,
            @Value("${rag.provider.embedding.default-dimension:768}") int defaultDimension) {
        return new RemoteEmbeddingModel(providerHttpClient, defaultDimension);
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
                                             EmbeddingModelPort embeddingModel, VectorStorePort vectorStore,
                                             @Value("${rag.embedding.batch-size:100}") int maxEmbeddingBatchSize) {
        return new IngestionService(parser, splitter, embeddingModel, vectorStore, maxEmbeddingBatchSize);
    }

    @Bean
    public AsyncIngestionService asyncIngestionService(
            IngestionService ingestionService, VectorStorePort vectorStore, Tracer tracer) {
        return new AsyncIngestionService(ingestionService, vectorStore, null, tracer);
    }

    @Bean
    public LexicalScorer lexicalScorer() {
        return new LexicalScorer();
    }

    @Bean
    public AdvancedRetrievalService advancedRetrievalService(
            VectorStorePort vectorStore, LexicalScorer lexicalScorer) {
        return new AdvancedRetrievalService(vectorStore, lexicalScorer);
    }

    @Bean
    public ChatModelPort chatModel(RemoteChatModelPort remoteChatModelPort) {
        return remoteChatModelPort;
    }

    @Bean
    public StreamingChatModelPort streamingChatModel(RemoteChatModelPort remoteChatModelPort) {
        return remoteChatModelPort;
    }

    @Bean
    public RemoteChatModelPort remoteChatModelPort(
            ProviderHttpClient providerHttpClient, ObjectMapper objectMapper, Tracer tracer) {
        return new RemoteChatModelPort(providerHttpClient, objectMapper, tracer);
    }

    @Bean
    public ChatService chatService(VectorStorePort vectorStore, ChatModelPort chatModel,
                                   StreamingChatModelPort streamingChatModel) {
        return new ChatService(vectorStore, chatModel, streamingChatModel);
    }

    @Bean
    public WebCrawlerClient webCrawlerClient(
            RestClient.Builder restClientBuilder,
            @Value("${rag.webcrawler.url:http://localhost:8085}") String baseUrl) {
        return new WebCrawlerClient(restClientBuilder.clone().baseUrl(baseUrl).build());
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler(
            ChatService chatService, ObjectMapper objectMapper, Tracer tracer) {
        return new ChatWebSocketHandler(chatService, objectMapper, tracer);
    }

    @Bean
    public TraceHandshakeInterceptor traceHandshakeInterceptor(Tracer tracer) {
        return new TraceHandshakeInterceptor(tracer);
    }
}