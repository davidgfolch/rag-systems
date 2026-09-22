package com.rag.advanced.retrieval;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.MetadataKeys;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataFilterTest {

    private final MetadataFilter filter = MetadataFilter.of(Map.of(
            MetadataKeys.SOURCE_TYPE, "file", MetadataKeys.TITLE, "note.txt"));

    @Test
    void matchesWhenAllValuesPresentAndEqual() {
        var chunk = new Chunk("c1", "d1", "content", 0, Map.of(
                MetadataKeys.SOURCE_TYPE, "file", MetadataKeys.TITLE, "note.txt"));
        assertThat(filter.matches(chunk)).isTrue();
    }

    @Test
    void rejectsWhenAnyValueDiffers() {
        var chunk = new Chunk("c1", "d1", "content", 0, Map.of(
                MetadataKeys.SOURCE_TYPE, "web", MetadataKeys.TITLE, "note.txt"));
        assertThat(filter.matches(chunk)).isFalse();
    }

    @Test
    void rejectsWhenAKeyIsMissing() {
        var chunk = new Chunk("c1", "d1", "content", 0, Map.of(MetadataKeys.SOURCE_TYPE, "file"));
        assertThat(filter.matches(chunk)).isFalse();
    }

    @Test
    void emptyFilterMatchesEverything() {
        MetadataFilter empty = MetadataFilter.of(null);
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.matches(new Chunk("c1", "d1", "content", 0, Map.of()))).isTrue();
    }
}