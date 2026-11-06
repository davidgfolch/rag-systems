package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonDocumentParserTest {

    private final JsonDocumentParser sut = new JsonDocumentParser();

    @Test
    void emitsOneBlockPerArrayRecordWithItsPath() {
        var document = new Document("d1", """
                [{"name":"Ada"},{"name":"Linus"}]
                """, Map.of());
        var parsed = sut.parse(document);
        assertThat(parsed.sourceType()).isEqualTo(SourceType.JSON);
        assertThat(parsed.metadata()).containsEntry(FORMAT, "JSON");
        assertThat(parsed.blocks()).hasSize(2);
        assertThat(parsed.blocks().get(0).location().jsonPath()).isEqualTo("$[0]");
        assertThat(parsed.blocks().get(1).location().jsonPath()).isEqualTo("$[1]");
        assertThat(parsed.blocks()).extracting(DocumentBlock::type).containsOnly(BlockType.JSON_OBJECT);
    }

    @Test
    void keepsNestedObjectAndArrayPaths() {
        var document = new Document("d1", """
                {"user":{"name":"Ada"},"tags":["x","y"]}
                """, Map.of());
        var blocks = sut.parse(document).blocks();
        assertThat(blocks).extracting(block -> block.location().jsonPath())
                .contains("$.user", "$.tags[0]", "$.tags[1]");
        var tag = blocks.stream().filter(block -> "x".equals(block.content())).findFirst().orElseThrow();
        assertThat(tag.sectionPath()).containsExactly("tags");
    }

    @Test
    void groupsScalarFieldsIntoOneObjectBlock() {
        var document = new Document("d1", """
                {"name":"Ada","age":36}
                """, Map.of());
        var blocks = sut.parse(document).blocks();
        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().location().jsonPath()).isEqualTo("$");
        assertThat(blocks.getFirst().content()).contains("Ada").contains("36");
    }

    @Test
    void emitsScalarRootAsOneBlock() {
        var blocks = sut.parse(new Document("d1", "\"hello\"", Map.of())).blocks();
        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().content()).contains("hello");
    }

    @Test
    void rejectsInvalidJsonInsteadOfFlatteningIt() {
        var document = new Document("d1", "{not json", Map.of());
        assertThatThrownBy(() -> sut.parse(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse JSON");
    }

    @Test
    void rejectsEmptyJson() {
        var document = new Document("d1", " ", Map.of());
        assertThatThrownBy(() -> sut.parse(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Empty JSON document");
    }
}
