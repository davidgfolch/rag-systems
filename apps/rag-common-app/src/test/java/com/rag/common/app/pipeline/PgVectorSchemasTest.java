package com.rag.common.app.pipeline;

import com.rag.common.generation.adapter.RemoteEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PgVectorSchemasTest {

    @Test
    void quotesIdentifiersAndDropsBlankOnes() {
        assertThat(PgVectorSchemas.quote(null)).isEmpty();
        assertThat(PgVectorSchemas.quote("  ")).isEmpty();
        assertThat(PgVectorSchemas.quote("rag_basic")).isEqualTo("\"rag_basic\"");
        assertThat(PgVectorSchemas.quote("we\"ird")).isEqualTo("\"we\"\"ird\"");
        assertThat(PgVectorSchemas.qualified("rag_basic", "variant_v2"))
                .isEqualTo("\"rag_basic\".\"variant_v2\"");
    }

    @Test
    void prefersAConfiguredSchemaOverride() {
        assertThat(PgVectorSchemas.resolve(" rag_alt ", 768)).isEqualTo("rag_alt");
        assertThat(PgVectorSchemas.resolve("rag_alt", mock(EmbeddingModel.class))).isEqualTo("rag_alt");
    }

    @Test
    void derivesTheSchemaFromTheVariantDimension() {
        assertThat(PgVectorSchemas.resolve(null, 768)).isEqualTo("rag_768");
        assertThat(PgVectorSchemas.resolve("   ", 1024)).isEqualTo("rag_1024");
        assertThat(PgVectorSchemas.resolve(null, 0)).isEqualTo("public");
    }

    @Test
    void derivesTheSchemaFromARemoteModelsProbedDimension() {
        var remote = mock(RemoteEmbeddingModel.class);
        when(remote.dimensions()).thenReturn(1024);

        assertThat(PgVectorSchemas.resolve(null, remote)).isEqualTo("rag_1024");
        assertThat(PgVectorSchemas.resolve(null, mock(EmbeddingModel.class))).isEqualTo("public");
    }
}
