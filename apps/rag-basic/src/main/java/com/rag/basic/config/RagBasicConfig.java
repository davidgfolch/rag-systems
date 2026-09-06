package com.rag.basic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.basic.api.chat.ChatWebSocketHandler;
import com.rag.basic.services.RetrievalService;
import com.rag.basic.services.WebCrawlerClient;
import com.rag.common.adapter.SpringAiChatModel;
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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

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
    public EmbeddingModel localSpringAiEmbeddingModel(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}") String model) {
        var api = OllamaApi.builder().baseUrl(baseUrl).build();
        var options = OllamaEmbeddingOptions.builder().model(model).build();
        return OllamaEmbeddingModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    @Bean
    @Profile("cloud")
    public EmbeddingModel cloudSpringAiEmbeddingModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String model) {
        var api = OpenAiApi.builder().apiKey(apiKey).build();
        var options = OpenAiEmbeddingOptions.builder().model(model).build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
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
    public VectorStorePort vectorStore(
            @Value("${rag.vector-store.type:pgvector}") String type,
            EmbeddingModelPort embeddingModel,
            ObjectProvider<PgVectorStore> pgVectorStore,
            ObjectProvider<DataSource> dataSource,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schema,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName) {
        if ("simple".equalsIgnoreCase(type)) {
            return new InMemoryVectorStore(embeddingModel);
        }
        return new PgVectorStoreAdapter(pgVectorStore.getObject(), dataSource.getIfAvailable(), schema, tableName);
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
    @Profile("local")
    public ChatModel providerChatModel(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.chat.options.model:phi4}") String model) {
        var api = OllamaApi.builder().baseUrl(baseUrl).build();
        var options = OllamaChatOptions.builder().model(model).build();
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }

    @Bean
    @Profile("cloud")
    public ChatModel cloudProviderChatModel(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o}") String model) {
        var api = OpenAiApi.builder().apiKey(apiKey).build();
        var options = OpenAiChatOptions.builder().model(model).build();
        return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
    }

    @Bean
    public ChatModelPort chatModel(ChatClient.Builder builder) {
        return new SpringAiChatModel(builder.build());
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