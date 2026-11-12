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

import static com.rag.common.core.domain.MetadataKeys.CODE_LANGUAGE;
import static org.assertj.core.api.Assertions.assertThat;

class SectionAwareBlockGrouperTest {

    private static final int BIG = 10_000;

    private final SectionAwareBlockGrouper sut = new SectionAwareBlockGrouper();

    private static DocumentBlock paragraph(String text) {
        return new DocumentBlock(BlockType.PARAGRAPH, text, List.of("Guide"), SourceLocation.NONE, Map.of());
    }

    private static ParsedDocument document(DocumentBlock... blocks) {
        return new ParsedDocument("d1", SourceType.TEXT, Map.of(), List.of(blocks));
    }

    @Test
    void usesHeadingsAsContextWithoutEmittingThemAsGroups() {
        var doc = document(
                new DocumentBlock(BlockType.HEADING, "Guide", List.of("Guide"), SourceLocation.NONE, Map.of()),
                paragraph("Body text."));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, BIG);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().sectionPath()).containsExactly("Guide");
        assertThat(groups.getFirst().text()).isEqualTo("Body text.");
        assertThat(groups.getFirst().context()).isEqualTo("Guide");
    }

    @Test
    void flushesWhenTheSectionChanges() {
        var doc = document(
                new DocumentBlock(BlockType.HEADING, "A", List.of("A"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.PARAGRAPH, "one", List.of("A"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.HEADING, "B", List.of("B"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.PARAGRAPH, "two", List.of("B"), SourceLocation.NONE, Map.of()));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, BIG);
        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(BlockGroup::sectionPath)
                .containsExactly(List.of("A"), List.of("B"));
    }

    @Test
    void keepsTablesAndCodeInTheirOwnProtectedGroups() {
        var doc = document(
                paragraph("intro"),
                new DocumentBlock(BlockType.TABLE, "cell", List.of("Guide"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.CODE, "code", List.of("Guide"), SourceLocation.NONE,
                        Map.of(CODE_LANGUAGE, "java")),
                paragraph("outro"));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, BIG);
        assertThat(groups).hasSize(4);
        assertThat(groups.get(1).protectedBlock()).isTrue();
        assertThat(groups.get(1).contentType()).isEqualTo(BlockType.TABLE);
        assertThat(groups.get(2).protectedBlock()).isTrue();
        assertThat(groups.get(2).metadata()).containsEntry(CODE_LANGUAGE, "java");
    }

    @Test
    void flushesWhenThePageChanges() {
        var doc = document(
                new DocumentBlock(BlockType.PARAGRAPH, "one", List.of("A"), new SourceLocation(1, null, null, null, null), Map.of()),
                new DocumentBlock(BlockType.PARAGRAPH, "two", List.of("A"), new SourceLocation(2, null, null, null, null), Map.of()));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, BIG);
        assertThat(groups).hasSize(2);
        assertThat(groups).extracting(group -> group.location().page()).containsExactly(1, 2);
    }

    @Test
    void flushesWhenTheCharacterWindowIsExceeded() {
        var doc = document(paragraph("aaaa"), paragraph("bbbb"));
        var groups = sut.group(doc, ChunkingConfig.DEFAULT, 5);
        assertThat(groups).hasSize(2);
    }

    @Test
    void aggregatesSectionWhenBoundariesAreNotRespected() {
        var doc = document(
                new DocumentBlock(BlockType.PARAGRAPH, "one", List.of("X"), SourceLocation.NONE, Map.of()));
        var withoutBoundaries = new ChunkingConfig(5, ChunkingConfig.NO_TOKEN_LIMIT, true, true, false);
        var groups = sut.group(doc, withoutBoundaries, BIG);
        assertThat(groups.getFirst().sectionPath()).containsExactly("X");
    }
}
