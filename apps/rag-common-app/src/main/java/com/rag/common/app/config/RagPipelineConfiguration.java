package com.rag.common.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.app.pipeline.ActiveEmbeddingModelPort;
import com.rag.common.app.pipeline.ActiveTextSplitter;
import com.rag.common.app.pipeline.ActiveVectorStore;
import com.rag.common.app.pipeline.Chunkers;
import com.rag.common.app.pipeline.PgVectorSchemas;
import com.rag.common.app.pipeline.PipelineVariantController;
import com.rag.common.app.pipeline.VariantStore;
import com.rag.common.app.pipeline.VariantStoreFactory;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.services.ChatModelPort;
import com.rag.common.core.services.DocumentParser;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.core.services.TextSplitter;
import com.rag.common.generation.ChatService;
import com.rag.common.generation.StreamingChatModelPort;
import com.rag.common.generation.adapter.ProviderHttpClient;
import com.rag.common.generation.adapter.RemoteChatModelPort;
import com.rag.common.generation.adapter.RemoteEmbeddingModel;
import com.rag.common.generation.ws.TraceHandshakeInterceptor;
import com.rag.common.ingestion.AsyncIngestionService;
import com.rag.common.ingestion.IngestionService;
import com.rag.common.ingestion.parsing.PlainTextParser;
import com.rag.common.ingestion.parsing.TikaDocumentParser;
import com.rag.common.retrieval.store.InMemoryVectorStore;
import com.rag.common.retrieval.store.PgVectorStoreAdapter;
import com.rag.contract.pipeline.ChunkingStrategy;
import com.rag.contract.pipeline.PipelineVariantDTO;
import io.micrometer.tracing.Tracer;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Shared wiring for every runnable RAG module. Owns the strategy beans that are
 * identical across rag-basic/rag-advanced/rag-agentic so the per-module configs
 * only keep their distinguishing beans. Imported by each module's config via
 * {@code @Import}; not component-scanned (apps scan only their own package).
 *
 * <p>The splitter and vector store delegate to the module's active pipeline
 * variant, so a variant switch applies to the next ingestion and query without
 * a restart.
 */
@Configuration
public class RagPipelineConfiguration {

    private static final String BOOT_VARIANT_ID = "boot";
    private static final String BOOT_MODEL_LABEL = "rag-provider";
    private static final String BOOT_STORE_BEAN = "bootVectorStore";

    @Bean(BOOT_STORE_BEAN)
    @Lazy
    public VectorStorePort bootVectorStore(
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
        var schema = PgVectorSchemas.resolve(schemaOverride, springAiEmbeddingModel);
        return new PgVectorStoreAdapter(pgVectorStore.getObject(), dataSource.getIfAvailable(), schema, tableName);
    }

    @Bean
    public VariantStoreFactory variantStoreFactory(
            ObjectProvider<JdbcTemplate> jdbcTemplate,
            ObjectProvider<DataSource> dataSource,
            EmbeddingModel springAiEmbeddingModel,
            ProviderHttpClient providerHttpClient,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:}") String schemaOverride) {
        return new VariantStoreFactory(jdbcTemplate, dataSource,
                variant -> BOOT_VARIANT_ID.equals(variant.variantId())
                        ? springAiEmbeddingModel
                        : new RemoteEmbeddingModel(providerHttpClient, variant.embeddingDimension(),
                        variant.embeddingModel()),
                springAiEmbeddingModel, schemaOverride);
    }

    @Bean
    public VariantStore variantStore(
            @Value("${spring.application.name:rag}") String module,
            @Value("${rag.chunking.strategy:recursive}") String strategy,
            @Value("${rag.chunking.size:512}") int size,
            @Value("${rag.chunking.overlap:128}") int overlap,
            @Value("${rag.provider.embedding.default-dimension:768}") int dimension,
            VariantStoreFactory storeFactory,
            @Qualifier(BOOT_STORE_BEAN) VectorStorePort bootStore) {
        var bootStrategy = ChunkingStrategy.from(strategy).orElse(ChunkingStrategy.RECURSIVE);
        var store = new VariantStore(module, Chunkers.of(bootStrategy, size, overlap),
                storeFactory, bootStore, storeFactory.repository());
        store.seed(BOOT_VARIANT_ID, new PipelineVariantDTO(BOOT_VARIANT_ID, module, bootStrategy.name(),
                BOOT_MODEL_LABEL, dimension, size, overlap));
        store.restore();
        return store;
    }

    @Bean
    public PipelineVariantController pipelineVariantController(VariantStore variantStore) {
        return new PipelineVariantController(variantStore);
    }

    @Bean
    public TextSplitter textSplitter(VariantStore variantStore) {
        return new ActiveTextSplitter(variantStore);
    }

    @Bean
    @Primary
    public VectorStorePort vectorStore(VariantStore variantStore) {
        return new ActiveVectorStore(variantStore);
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
    public EmbeddingModelPort embeddingModel(ObjectProvider<VariantStore> variantStore) {
        return new ActiveEmbeddingModelPort(variantStore);
    }

    @Bean
    @Lazy
    public PgVectorStore pgVectorStore(
            JdbcTemplate jdbcTemplate,
            EmbeddingModel springAiEmbeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:}") String schemaOverride,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName,
            @Value("${spring.ai.vectorstore.pgvector.initialize-schema:true}") boolean initializeSchema) {
        var schema = PgVectorSchemas.resolve(schemaOverride, springAiEmbeddingModel);
        return PgVectorStore.builder(jdbcTemplate, springAiEmbeddingModel)
                .schemaName(schema)
                .vectorTableName(tableName)
                .initializeSchema(initializeSchema)
                .build();
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
    public TraceHandshakeInterceptor traceHandshakeInterceptor(Tracer tracer) {
        return new TraceHandshakeInterceptor(tracer);
    }
}
