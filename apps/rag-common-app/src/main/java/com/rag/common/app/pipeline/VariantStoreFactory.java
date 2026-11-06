package com.rag.common.app.pipeline;

import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.retrieval.store.PgVectorStoreAdapter;
import com.rag.contract.pipeline.PipelineVariantDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.function.Function;

/**
 * Builds the store behind one pipeline variant. The variant owns a
 * {@code variant_<id>} table inside the module schema, so switching variants
 * writes into fresh storage instead of reusing another variant's vectors.
 *
 * <p>Stores are built lazily on first use: a module with no PostgreSQL
 * datasource (e.g. the in-memory profile) only fails if a variant that needs
 * storage is actually activated.
 */
public class VariantStoreFactory implements VariantStores {

    private static final Logger log = LoggerFactory.getLogger(VariantStoreFactory.class);

    private static final String TABLE_PREFIX = "variant_";

    private final ObjectProvider<JdbcTemplate> jdbcTemplate;
    private final ObjectProvider<DataSource> dataSource;
    private final Function<PipelineVariantDTO, EmbeddingModel> embeddingModels;
    private final EmbeddingModel defaultEmbeddingModel;
    private final String schemaOverride;

    public VariantStoreFactory(ObjectProvider<JdbcTemplate> jdbcTemplate, ObjectProvider<DataSource> dataSource,
                               Function<PipelineVariantDTO, EmbeddingModel> embeddingModels,
                               EmbeddingModel defaultEmbeddingModel, String schemaOverride) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.embeddingModels = embeddingModels;
        this.defaultEmbeddingModel = defaultEmbeddingModel;
        this.schemaOverride = schemaOverride;
    }

    @Override
    public VectorStorePort create(PipelineVariantDTO variant) {
        var jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            throw new IllegalStateException("Variant " + variant.variantId()
                    + " needs a PostgreSQL datasource, which this module does not have");
        }
        var model = embeddingModels.apply(variant);
        var schema = PgVectorSchemas.resolve(schemaOverride, variant.embeddingDimension());
        var table = TABLE_PREFIX + variant.variantId();
        var pgStore = PgVectorStore.builder(jdbc, model)
                .schemaName(schema)
                .vectorTableName(table)
                .initializeSchema(true)
                .build();
        return new PgVectorStoreAdapter(pgStore, dataSource.getIfAvailable(), schema, table);
    }

    @Override
    public EmbeddingModel embedding(PipelineVariantDTO variant) {
        return embeddingModels.apply(variant);
    }

    /**
     * Drops the variant's table. Safe to call for a variant whose table was
     * never created ({@code IF EXISTS}) and for a module without PostgreSQL,
     * where nothing was persisted in the first place.
     */
    @Override
    @SuppressWarnings("java:S2077")
    public void drop(PipelineVariantDTO variant) {
        var jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            return;
        }
        var table = PgVectorSchemas.qualified(
                PgVectorSchemas.resolve(schemaOverride, variant.embeddingDimension()),
                TABLE_PREFIX + variant.variantId());
        try {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
            log.info("Dropped variant storage {}", table);
        } catch (RuntimeException e) {
            log.warn("Could not drop variant storage {}: {}", table, e.getMessage());
        }
    }

    /** Registry persistence for this module, or {@code null} when it has no PostgreSQL. */
    public VariantRepository repository() {
        var jdbc = jdbcTemplate.getIfAvailable();
        if (jdbc == null) {
            return null;
        }
        return new VariantRepository(jdbc, PgVectorSchemas.resolve(schemaOverride, defaultEmbeddingModel));
    }
}
