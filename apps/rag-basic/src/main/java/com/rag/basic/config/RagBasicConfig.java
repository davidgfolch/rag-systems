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
import com.rag.common.services.AsyncIngestionService;
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
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
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
    public DocumentParser documentParser(@Value("${rag.parsing.mode:tika}") String mode) {
        return "tika".equalsIgnoreCase(mode) ? new TikaDocumentParser() : new PlainTextParser();
    }

    @Bean
    @Profile("local")
    public org.springframework.ai.embedding.EmbeddingModel localSpringAiEmbeddingModel(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}") String model) {
        var api = org.springframework.ai.ollama.api.OllamaApi.builder().baseUrl(baseUrl).build();
        var options = org.springframework.ai.ollama.api.OllamaEmbeddingOptions.builder().model(model).build();
        return OllamaEmbeddingModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    @Bean
    @Profile("cloud")
    public org.springframework.ai.embedding.EmbeddingModel cloudSpringAiEmbeddingModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String model) {
        var api = org.springframework.ai.openai.api.OpenAiApi.builder().apiKey(apiKey).build();
        var options = org.springframework.ai.openai.OpenAiEmbeddingOptions.builder().model(model).build();
        return new OpenAiEmbeddingModel(api, org.springframework.ai.document.MetadataMode.EMBED, options);
    }

    @Bean
    public EmbeddingModel embeddingModel(org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel) {
        return new SpringAiEmbeddingModel(springAiEmbeddingModel);
    }

    @Bean
    @Lazy
    public PgVectorStore pgVectorStore(
            JdbcTemplate jdbcTemplate,
            org.springframework.ai.embedding.EmbeddingModel springAiEmbeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schema,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName,
            @Value("${spring.ai.vectorstore.pgvector.initialize-schema:true}") boolean initializeSchema) {
        return PgVectorStore.builder(jdbcTemplate, springAiEmbeddingModel)
                .schemaName(schema)
                .vectorTableName(tableName)
                .initializeSchema(initializeSchema)
                .build();
    }

    @Bean
    @Lazy
    public VectorStore vectorStore(
            @Value("${rag.vector-store.type:pgvector}") String type,
            EmbeddingModel embeddingModel,
            ObjectProvider<PgVectorStore> pgVectorStore,
            ObjectProvider<javax.sql.DataSource> dataSource,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schema,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName) {
        if ("simple".equalsIgnoreCase(type)) {
            return new InMemoryVectorStore(embeddingModel);
        }
        return new PgVectorStoreAdapter(pgVectorStore.getObject(), dataSource.getIfAvailable(), schema, tableName);
    }

    @Bean
    public IngestionService ingestionService(DocumentParser parser, TextSplitter splitter,
                                             EmbeddingModel embeddingModel, VectorStore vectorStore) {
        return new IngestionService(parser, splitter, embeddingModel, vectorStore);
    }

    @Bean
    public AsyncIngestionService asyncIngestionService(IngestionService ingestionService, VectorStore vectorStore) {
        return new AsyncIngestionService(ingestionService, vectorStore, null);
    }

    @Bean
    public RetrievalService retrievalService(VectorStore vectorStore) {
        return new RetrievalService(vectorStore);
    }

    @Bean
    @Profile("local")
    public org.springframework.ai.chat.model.ChatModel providerChatModel(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.chat.options.model:phi4}") String model) {
        var api = org.springframework.ai.ollama.api.OllamaApi.builder().baseUrl(baseUrl).build();
        var options = org.springframework.ai.ollama.api.OllamaChatOptions.builder().model(model).build();
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    @Bean
    @Profile("cloud")
    public org.springframework.ai.chat.model.ChatModel cloudProviderChatModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o}") String model) {
        var api = org.springframework.ai.openai.api.OpenAiApi.builder().apiKey(apiKey).build();
        var options = org.springframework.ai.openai.OpenAiChatOptions.builder().model(model).build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
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