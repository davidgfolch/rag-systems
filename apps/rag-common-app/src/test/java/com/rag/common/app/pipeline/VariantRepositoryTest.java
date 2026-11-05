package com.rag.common.app.pipeline;

import com.rag.contract.pipeline.PipelineVariantDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VariantRepositoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VariantRepository sut = new VariantRepository(jdbc, "rag_basic");

    private static PipelineVariantDTO variant(String id, int size, int overlap) {
        return new PipelineVariantDTO(id, "rag-basic", "RECURSIVE", "nomic-embed-text", 768, size, overlap);
    }

    @Test
    void createsTheSchemaAndRegistryTable() {
        sut.ensureTable();
        verify(jdbc).execute(contains("CREATE SCHEMA IF NOT EXISTS \"rag_basic\""));
        verify(jdbc).execute(contains("CREATE TABLE IF NOT EXISTS \"rag_basic\".\"pipeline_variants\""));
    }

    @Test
    void replaceAllRewritesTheRegistryWithBootAndActiveFlags() {
        sut.replaceAll(List.of(variant("boot", 256, 32), variant("v2", 64, 8)), "v2", "boot");
        verify(jdbc).update("DELETE FROM \"rag_basic\".\"pipeline_variants\"");
        verify(jdbc).update(contains("INSERT INTO \"rag_basic\".\"pipeline_variants\""),
                eq("boot"), eq("RECURSIVE"), eq("nomic-embed-text"), eq(768), eq(256), eq(32),
                eq(true), eq(false));
        verify(jdbc).update(contains("INSERT INTO \"rag_basic\".\"pipeline_variants\""),
                eq("v2"), eq("RECURSIVE"), eq("nomic-embed-text"), eq(768), eq(64), eq(8),
                eq(false), eq(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadMapsRowsIntoPersistedVariants() throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.getString("variant_id")).thenReturn("v2");
        when(rs.getString("strategy")).thenReturn("TOKEN");
        when(rs.getString("embedding_model")).thenReturn("nomic-embed-text");
        when(rs.getInt("embedding_dimension")).thenReturn(768);
        when(rs.getInt("chunk_size")).thenReturn(256);
        when(rs.getInt("chunk_overlap")).thenReturn(64);
        when(rs.getBoolean("is_boot")).thenReturn(false);
        when(rs.getBoolean("is_active")).thenReturn(true);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<VariantRepository.PersistedVariant>>any()))
                .thenAnswer(call -> List.of(((RowMapper<VariantRepository.PersistedVariant>) call.getArgument(1))
                        .mapRow(rs, 0)));

        var loaded = sut.load();

        assertThat(loaded).singleElement().satisfies(row -> {
            assertThat(row.variant().variantId()).isEqualTo("v2");
            assertThat(row.variant().strategy()).isEqualTo("TOKEN");
            assertThat(row.variant().chunkSize()).isEqualTo(256);
            assertThat(row.boot()).isFalse();
            assertThat(row.active()).isTrue();
        });
    }
}
