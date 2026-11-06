package com.rag.common.ingestion.chunking.structural;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceLocation;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.ChunkingConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonBlockGrouperTest {

    private static final int BIG = 10_000;

    private final JsonBlockGrouper sut = new JsonBlockGrouper();

    private static DocumentBlock jsonRecord(String content, String path) {
        return new DocumentBlock(BlockType.JSON_OBJECT, content, List.of("items"),
                new SourceLocation(null, null, path, null, null), Map.of());
    }

    private static ParsedDocument document(DocumentBlock... blocks) {
        return new ParsedDocument("d1", SourceType.JSON, Map.of(), List.of(blocks));
    }

    @Test
    void groupsSiblingRecordsUpToTheConfiguredCount() {
        var doc = document(jsonRecord("a", "$[0]"), jsonRecord("b", "$[1]"), jsonRecord("c", "$[2]"));
        var config = new ChunkingConfig(2, ChunkingConfig.NO_TOKEN_LIMIT, true, true, true);
        var groups = sut.group(doc, config, BIG);
        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).text()).isEqualTo("a\n\nb");
        assertThat(groups.get(1).text()).isEqualTo("c");
        assertThat(groups).extracting(BlockGroup::contentType).containsOnly(BlockType.JSON_OBJECT);
    }

    @Test
    void flushesWhenTheJsonPathChanges() {
        var doc = document(jsonRecord("a", "$[0]"),
                new DocumentBlock(BlockType.JSON_OBJECT, "b", List.of("other"),
                        new SourceLocation(null, null, "$.other", null, null), Map.of()));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, BIG);
        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(BlockGroup::sectionPath)
                .containsExactly(List.of("items"), List.of("other"));
    }

    @Test
    void flushesWhenTheCharacterWindowIsExceeded() {
        var doc = document(jsonRecord("aaaa", "$[0]"), jsonRecord("bbbb", "$[1]"));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, 5);
        assertThat(groups).hasSize(2);
    }

    @Test
    void marksArrayGroupsAsJsonArrays() {
        var doc = document(new DocumentBlock(BlockType.JSON_ARRAY, "1", List.of(), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.JSON_ARRAY, "2", List.of(), SourceLocation.NONE, Map.of()));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, BIG);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().contentType()).isEqualTo(BlockType.JSON_ARRAY);
    }

    @Test
    void ignoresHeadingsAndBlankBlocks() {
        var doc = document(
                new DocumentBlock(BlockType.HEADING, "Items", List.of("Items"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.JSON_OBJECT, " ", List.of("Items"), SourceLocation.NONE, Map.of()),
                jsonRecord("value", "$[0]"));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, BIG);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().text()).isEqualTo("value");
    }
}
