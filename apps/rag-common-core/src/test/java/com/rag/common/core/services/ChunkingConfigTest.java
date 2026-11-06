package com.rag.common.core.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingConfigTest {

    @Test
    void defaultConfigPreservesStructure() {
        assertThat(ChunkingConfig.DEFAULT.maxRecordsPerChunk())
                .isEqualTo(ChunkingConfig.DEFAULT_MAX_RECORDS_PER_CHUNK);
        assertThat(ChunkingConfig.DEFAULT.maxTokens()).isEqualTo(ChunkingConfig.NO_TOKEN_LIMIT);
        assertThat(ChunkingConfig.DEFAULT.preserveTables()).isTrue();
        assertThat(ChunkingConfig.DEFAULT.preserveCode()).isTrue();
        assertThat(ChunkingConfig.DEFAULT.respectSectionBoundaries()).isTrue();
    }

    @Test
    void effectiveWindowUsesTheVariantSizeWithoutATokenLimit() {
        assertThat(ChunkingConfig.DEFAULT.effectiveMaxChars(512)).isEqualTo(512);
    }

    @Test
    void effectiveWindowIsCappedByTheTokenLimit() {
        var config = new ChunkingConfig(5, 50, true, true, true);
        assertThat(config.effectiveMaxChars(512)).isEqualTo(200);
        assertThat(new ChunkingConfig(5, 500, true, true, true).effectiveMaxChars(512)).isEqualTo(512);
    }

    @Test
    void rejectsAnInvalidRecordCount() {
        assertThatThrownBy(() -> new ChunkingConfig(0, 0, true, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRecordsPerChunk");
    }

    @Test
    void rejectsNegativeTokenLimitsAndWindows() {
        assertThatThrownBy(() -> new ChunkingConfig(1, -1, true, true, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTokens");
        assertThatThrownBy(() -> ChunkingConfig.DEFAULT.effectiveMaxChars(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("variantMaxChars");
    }

    @Test
    void comparesByValue() {
        var config = new ChunkingConfig(3, 10, false, true, false);
        assertThat(config).isEqualTo(new ChunkingConfig(3, 10, false, true, false))
                .hasSameHashCodeAs(new ChunkingConfig(3, 10, false, true, false))
                .isNotEqualTo(ChunkingConfig.DEFAULT);
        assertThat(config.toString()).contains("maxRecordsPerChunk=3");
    }
}
