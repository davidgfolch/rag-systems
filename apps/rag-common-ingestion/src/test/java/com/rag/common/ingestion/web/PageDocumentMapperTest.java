package com.rag.common.ingestion.web;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.SourceType;
import com.rag.contract.model.PageBlockDTO;
import com.rag.contract.model.PageDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.SOURCE;
import static com.rag.common.core.domain.MetadataKeys.SOURCE_TYPE;
import static com.rag.common.core.domain.MetadataKeys.TITLE;
import static org.assertj.core.api.Assertions.assertThat;

class PageDocumentMapperTest {

    @Test
    void mapsBlocksWithSectionPathAndDomProvenance() {
        var page = new PageDTO().url("https://example.com").title("Example").text("flat text")
                .blocks(List.of(
                        new PageBlockDTO("HEADING", "Guide").sectionPath(List.of("Guide")).domPath("h1"),
                        new PageBlockDTO("PARAGRAPH", "Body").sectionPath(List.of("Guide")).domPath("p")));
        var parsed = PageDocumentMapper.toParsedDocument("d1", page);
        assertThat(parsed.sourceType()).isEqualTo(SourceType.HTML);
        assertThat(parsed.metadata()).containsEntry(SOURCE_TYPE, "web")
                .containsEntry(SOURCE, "https://example.com")
                .containsEntry(FORMAT, "HTML")
                .containsEntry(TITLE, "Example");
        assertThat(parsed.blocks()).extracting(DocumentBlock::type)
                .containsExactly(BlockType.HEADING, BlockType.PARAGRAPH);
        assertThat(parsed.blocks().get(1).sectionPath()).containsExactly("Guide");
        assertThat(parsed.blocks().get(1).location().domPath()).isEqualTo("p");
    }

    @Test
    void fallsBackToFlattenedTextWhenNoBlocksWereExtracted() {
        var page = new PageDTO().url("https://example.com").title("Example").text("flat text");
        var parsed = PageDocumentMapper.toParsedDocument("d1", page);
        assertThat(parsed.blocks()).hasSize(1);
        assertThat(parsed.blocks().getFirst().type()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(parsed.blocks().getFirst().content()).isEqualTo("flat text");
    }

    @Test
    void treatsUnknownBlockTypesAsParagraphs() {
        var page = new PageDTO().url("https://example.com").text("text")
                .blocks(List.of(new PageBlockDTO("mystery", "content")));
        var parsed = PageDocumentMapper.toParsedDocument("d1", page);
        assertThat(parsed.blocks().getFirst().type()).isEqualTo(BlockType.PARAGRAPH);
    }
}
