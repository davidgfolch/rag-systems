package com.rag.common.app.pipeline;

import com.rag.contract.pipeline.PipelineVariantDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Keeps the pipeline variant registry in a {@code pipeline_variants} table
 * inside the module schema, so restarting a module restores the variants it had
 * instead of falling back to only the configuration-seeded boot variant.
 *
 * <p>The whole registry is rewritten on every change: the table stays tiny and
 * "which variant is boot/active" is persisted alongside its definition, so no
 * reconciliation is needed on load.
 */
public class VariantRepository {

    private static final Logger log = LoggerFactory.getLogger(VariantRepository.class);

    static final String TABLE = "pipeline_variants";

    private final JdbcTemplate jdbc;
    private final String schemaName;
    private final String schema;
    private final String qualifiedTable;
    private volatile boolean ready;

    public VariantRepository(JdbcTemplate jdbc, String schema) {
        this.jdbc = jdbc;
        this.schemaName = schema;
        this.schema = PgVectorSchemas.quote(schema);
        this.qualifiedTable = PgVectorSchemas.qualified(schema, TABLE);
    }

    /** Schema this module keeps its registry and vector tables in. */
    public String schemaName() {
        return schemaName;
    }

    /** One persisted row plus the flags that describe its role on restart. */
    public record PersistedVariant(PipelineVariantDTO variant, boolean boot, boolean active) {
    }

    @SuppressWarnings("java:S2077")
    public void ensureTable() {
        if (ready) {
            return;
        }
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + qualifiedTable + " ("
                + "variant_id varchar(64) PRIMARY KEY, "
                + "strategy varchar(16) NOT NULL, "
                + "embedding_model varchar(200) NOT NULL DEFAULT '', "
                + "embedding_dimension integer NOT NULL, "
                + "chunk_size integer NOT NULL, "
                + "chunk_overlap integer NOT NULL, "
                + "is_boot boolean NOT NULL DEFAULT false, "
                + "is_active boolean NOT NULL DEFAULT false)");
        ready = true;
        log.debug("Variant registry table ready at {}", qualifiedTable);
    }

    @SuppressWarnings("java:S2077")
    public List<PersistedVariant> load() {
        return jdbc.query("SELECT variant_id, strategy, embedding_model, embedding_dimension, "
                        + "chunk_size, chunk_overlap, is_boot, is_active FROM " + qualifiedTable,
                (rs, _) -> new PersistedVariant(
                        new PipelineVariantDTO(rs.getString("variant_id"), null, rs.getString("strategy"),
                                rs.getString("embedding_model"), rs.getInt("embedding_dimension"),
                                rs.getInt("chunk_size"), rs.getInt("chunk_overlap")),
                        rs.getBoolean("is_boot"), rs.getBoolean("is_active")));
    }

    @SuppressWarnings("java:S2077")
    public void replaceAll(List<PipelineVariantDTO> variants, String activeId, String bootId) {
        // The boot variant is seeded before restore() runs, so the first write has to create the table itself.
        ensureTable();
        jdbc.update("DELETE FROM " + qualifiedTable);
        for (var variant : variants) {
            jdbc.update("INSERT INTO " + qualifiedTable + " (variant_id, strategy, embedding_model, "
                            + "embedding_dimension, chunk_size, chunk_overlap, is_boot, is_active) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    variant.variantId(), variant.strategy(), variant.embeddingModel(),
                    variant.embeddingDimension(), variant.chunkSize(), variant.chunkOverlap(),
                    variant.variantId().equals(bootId), variant.variantId().equals(activeId));
        }
    }
}
