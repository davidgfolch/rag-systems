package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.BlockType;
import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.DocumentBlock;
import com.rag.common.core.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.RAW;
import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;
import static org.assertj.core.api.Assertions.assertThat;

class TikaXhtmlDocumentParserTest {

    private static final byte[] HTML = """
            <html><body><h1>Report</h1><p>Body text.</p></body></html>
            """.getBytes(StandardCharsets.UTF_8);

    private final TikaXhtmlExtractor extractor = new TikaXhtmlExtractor();
    private final TikaXhtmlDocumentParser sut = new TikaXhtmlDocumentParser(SourceType.PDF, extractor, false);

    @Test
    void walksExtractedXhtmlInsteadOfFlatteningIt() {
        var document = new Document("d1", "", Map.of(RAW_BYTES, HTML));
        var parsed = sut.parse(document);
        assertThat(parsed.sourceType()).isEqualTo(SourceType.PDF);
        assertThat(parsed.metadata()).containsEntry(FORMAT, "PDF");
        assertThat(parsed.blocks()).extracting(DocumentBlock::type).contains(BlockType.HEADING);
        assertThat(parsed.blocks().stream().map(DocumentBlock::content).toList())
                .anyMatch(content -> content.contains("Report"));
    }

    @Test
    void acceptsLegacyBase64RawContent() {
        var encoded = Base64.getEncoder().encodeToString(HTML);
        var document = new Document("d1", "", Map.of(RAW, encoded));
        var parsed = sut.parse(document);
        assertThat(parsed.blocks()).anyMatch(block -> block.content().contains("Report"));
    }

    @Test
    void fallsBackToDocumentContentWithoutRawBytes() {
        var document = new Document("d1", "already extracted text", Map.of());
        var parsed = sut.parse(document);
        assertThat(parsed.blocks()).hasSize(1);
        assertThat(parsed.blocks().getFirst().content()).isEqualTo("already extracted text");
    }
}
