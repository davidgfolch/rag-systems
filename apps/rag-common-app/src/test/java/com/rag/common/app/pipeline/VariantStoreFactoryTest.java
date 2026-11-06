package com.rag.common.app.pipeline;

import com.rag.contract.pipeline.PipelineVariantDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VariantStoreFactoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final Function<PipelineVariantDTO, EmbeddingModel> models = _ -> model;
    private final VariantStoreFactory sut = new VariantStoreFactory(
            provider(jdbc), provider(mock(DataSource.class)), models, model, "rag_basic");

    private static PipelineVariantDTO variant() {
        return new PipelineVariantDTO("v2", "rag-basic", "RECURSIVE", "nomic-embed-text", 768, 512, 64);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        var provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JdbcTemplate> withoutJdbc() {
        return mock(ObjectProvider.class);
    }

    @Test
    void createsAStoreOnTheVariantTableAndReusesTheModelFunction() {
        assertThat(sut.create(variant())).isNotNull();
        assertThat(sut.embedding(variant())).isSameAs(model);
    }

    @Test
    void dropsTheVariantTable() {
        sut.drop(variant());
        verify(jdbc).execute("DROP TABLE IF EXISTS \"rag_basic\".\"variant_v2\"");
    }

    @Test
    void aFailedDropIsLoggedInsteadOfFailingTheRemoval() {
        doThrow(new IllegalStateException("db down")).when(jdbc).execute(anyString());
        assertThatCode(() -> sut.drop(variant())).doesNotThrowAnyException();
    }

    @Test
    void exposesTheRegistryOnlyWhenPostgresIsAvailable() {
        assertThat(sut.repository()).isNotNull();
    }

    @Test
    void aModuleWithoutPostgresKeepsVariantsInMemory() {
        var withoutPostgres = new VariantStoreFactory(
                withoutJdbc(), provider(mock(DataSource.class)), models, model, null);

        assertThat(withoutPostgres.repository()).isNull();
        assertThatCode(() -> withoutPostgres.drop(variant())).doesNotThrowAnyException();
        var variant = variant();
        assertThatThrownBy(() -> withoutPostgres.create(variant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }
}
