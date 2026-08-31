package com.rag.common.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {

    private final Document sut = new Document("doc-1", "content", Map.of("src", "test"));

    @Test
    void shouldReturnIdContentAndMetadata() {
        assertThat(sut.getId()).isEqualTo("doc-1");
        assertThat(sut.getContent()).isEqualTo("content");
        assertThat(sut.getMetadata()).containsEntry("src", "test");
    }

    @Test
    void shouldImmutablyCopyMetadataOnConstruction() {
        assertThat(sut.getMetadata()).isNotSameAs(Map.of("src", "test"));
    }

    @Test
    void shouldUseImmutableCopyWhenMetadataIsNull() {
        assertThat(new Document("d", "c", null).getMetadata()).isEmpty();
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> new Document(null, "c", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldBeEqualOnSameId() {
        assertThat(new Document("doc-1", "other", null))
                .isEqualTo(sut)
                .hasSameHashCodeAs(sut);
    }
}