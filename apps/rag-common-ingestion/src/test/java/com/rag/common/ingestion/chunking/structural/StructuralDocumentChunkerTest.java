package com.rag.common.ingestion.chunking.structural;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Chunk;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceLocation;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.ChunkingConfig;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.CONTENT_TYPE;
import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;
import static com.rag.common.core.domain.MetadataKeys.SECTION_PATH;
import static com.rag.common.core.domain.MetadataKeys.STRATEGY;
import static com.rag.common.core.domain.MetadataKeys.TABLE_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuralDocumentChunkerTest {

    private final StructuralDocumentChunker sut = new StructuralDocumentChunker(
            SourceType.TEXT, new RecursiveCharacterChunker(1000, 0), new SectionAwareBlockGrouper(), 1000);

    private static ParsedDocument document(List<DocumentBlock> blocks) {
        return new ParsedDocument("d1", SourceType.TEXT, Map.of(RAW_BYTES, new byte[]{1}), blocks);
    }

    @Test
    void supportsOnlyItsConfiguredFormats() {
        assertThat(sut.supports(document(List.of()))).isTrue();
        var markdown = new ParsedDocument("d1", SourceType.MARKDOWN, Map.of(), List.of());
        assertThat(sut.supports(markdown)).isFalse();
    }

    @Test
    void returnsNoChunksForABlankDocument() {
        assertThat(sut.chunk(document(List.of()), ChunkingConfig.DEFAULT)).isEmpty();
        var blank = document(List.of(new DocumentBlock(BlockType.PARAGRAPH, " ")));
        assertThat(sut.chunk(blank, ChunkingConfig.DEFAULT)).isEmpty();
    }

    @Test
    void carriesSectionAndFormatProvenanceIntoChunkMetadata() {
        var doc = document(List.of(
                new DocumentBlock(BlockType.HEADING, "Guide", List.of("Guide"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.PARAGRAPH, "Body text.", List.of("Guide"), SourceLocation.NONE, Map.of())));
        var chunks = sut.chunk(doc, ChunkingConfig.DEFAULT);
        assertThat(chunks).hasSize(1);
        var chunk = chunks.getFirst();
        assertThat(chunk.getContent()).contains("Guide").contains("Body text.");
        assertThat(chunk.getMetadata())
                .containsEntry(FORMAT, "TEXT")
                .containsEntry(CONTENT_TYPE, "PARAGRAPH")
                .containsEntry(SECTION_PATH, "Guide")
                .containsEntry(STRATEGY, "recursive")
                .doesNotContainKey(RAW_BYTES);
    }

    @Test
    void keepsProtectedTablesAndCodeInSeparateChunks() {
        var doc = document(List.of(
                new DocumentBlock(BlockType.PARAGRAPH, "intro"),
                new DocumentBlock(BlockType.TABLE, "Name | Age", List.of(), SourceLocation.NONE,
                        Map.of(TABLE_HEADER, "Name | Age")),
                new DocumentBlock(BlockType.CODE, "int x = 1;", List.of(), SourceLocation.NONE, Map.of())));
        var chunks = sut.chunk(doc, ChunkingConfig.DEFAULT);
        assertThat(chunks).extracting(chunk -> chunk.getMetadata().get(CONTENT_TYPE))
                .containsExactly("PARAGRAPH", "TABLE", "CODE");
        assertThat(chunks.get(1).getMetadata()).containsEntry(TABLE_HEADER, "Name | Age");
    }

    @Test
    void numbersChunksSequentiallyAcrossSections() {
        var doc = document(List.of(
                new DocumentBlock(BlockType.HEADING, "A", List.of("A"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.PARAGRAPH, "first", List.of("A"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.HEADING, "B", List.of("B"), SourceLocation.NONE, Map.of()),
                new DocumentBlock(BlockType.PARAGRAPH, "second", List.of("B"), SourceLocation.NONE, Map.of())));
        var chunks = sut.chunk(doc, ChunkingConfig.DEFAULT);
        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(Chunk::getIndex).containsExactly(0, 1);
        assertThat(chunks).extracting(chunk -> chunk.getMetadata().get(SECTION_PATH))
                .containsExactly("A", "B");
    }

    @Test
    void usesTheDefaultConfigWhenNoneIsGiven() {
        var doc = document(List.of(new DocumentBlock(BlockType.PARAGRAPH, "text")));
        assertThat(sut.chunk(doc, null)).hasSize(1);
    }

    @Test
    void rejectsANonPositiveWindow() {
        var splitter = new RecursiveCharacterChunker(100, 0);
        assertThatThrownBy(() -> new StructuralDocumentChunker(SourceType.TEXT, splitter, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("variantMaxChars must be > 0");
    }
}
