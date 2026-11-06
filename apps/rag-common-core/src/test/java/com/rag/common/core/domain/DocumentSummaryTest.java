package com.rag.common.core.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSummaryTest {

    @Test
    void copiesMetadataAndRejectsANullDocumentId() {
        var metadata = Map.<String, Object>of("source", "https://example.com");
        var summary = new DocumentSummary("d1", 3, metadata);
        assertThat(summary.documentId()).isEqualTo("d1");
        assertThat(summary.chunkCount()).isEqualTo(3);
        assertThat(summary.metadata()).containsEntry("source", "https://example.com");
        assertThat(new DocumentSummary("d1", 0, null).metadata()).isEmpty();
        assertThatThrownBy(() -> new DocumentSummary(null, 0, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("documentId");
    }

    @Test
    void comparesByValue() {
        var summary = new DocumentSummary("d1", 3, Map.of("k", "v"));
        assertThat(summary).isEqualTo(new DocumentSummary("d1", 3, Map.of("k", "v")))
                .hasSameHashCodeAs(new DocumentSummary("d1", 3, Map.of("k", "v")))
                .isNotEqualTo(new DocumentSummary("d2", 3, Map.of("k", "v")));
        assertThat(summary.toString()).contains("d1");
    }
}
