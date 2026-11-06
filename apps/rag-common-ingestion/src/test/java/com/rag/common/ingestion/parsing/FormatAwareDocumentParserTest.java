package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.ParsedDocument;
import com.rag.common.core.domain.SourceType;
import com.rag.common.core.services.DocumentParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.rag.common.core.domain.MetadataKeys.FILE_NAME;
import static com.rag.common.core.domain.MetadataKeys.FORMAT;
import static com.rag.common.core.domain.MetadataKeys.RAW_BYTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatAwareDocumentParserTest {

    private final DocumentParser pdfParser = document ->
            new ParsedDocument(document.getId(), SourceType.PDF, Map.of(), List.of());
    private final DocumentParser fallback = document ->
            new ParsedDocument(document.getId(), SourceType.TEXT, Map.of(), List.of());

    @Test
    void dispatchesToTheParserDetectedFromMetadata() {
        var sut = new FormatAwareDocumentParser(Map.of(SourceType.PDF, pdfParser), fallback);
        var parsed = sut.parse(new Document("d1", "", Map.of(FORMAT, "PDF")));
        assertThat(parsed.sourceType()).isEqualTo(SourceType.PDF);
    }

    @Test
    void fallsBackWhenNoParserIsRegistered() {
        var sut = new FormatAwareDocumentParser(Map.of(SourceType.PDF, pdfParser), fallback);
        var parsed = sut.parse(new Document("d1", "plain", Map.of()));
        assertThat(parsed.sourceType()).isEqualTo(SourceType.TEXT);
    }

    @Test
    void decodesRawBytesForTextFormatsUploadedWithoutContent() {
        DocumentParser capturing = document ->
                new ParsedDocument(document.getId(), SourceType.TEXT, Map.of(), List.of());
        var sut = new FormatAwareDocumentParser(Map.of(SourceType.TEXT, capturing), fallback);
        var bytes = "raw file text".getBytes(StandardCharsets.UTF_8);
        var document = new Document("d1", "", Map.of(FILE_NAME, "note.txt", RAW_BYTES, bytes));
        assertThat(sut.parse(document).sourceType()).isEqualTo(SourceType.TEXT);
    }

    @Test
    void leavesBinaryFormatsUntouchedForTheirParser() {
        DocumentParser capturing = document -> {
            assertThat(document.getContent()).isEmpty();
            return new ParsedDocument(document.getId(), SourceType.PDF, Map.of(), List.of());
        };
        var sut = new FormatAwareDocumentParser(Map.of(SourceType.PDF, capturing), fallback);
        var document = new Document("d1", "", Map.of(FORMAT, "PDF", RAW_BYTES, new byte[]{1, 2}));
        assertThat(sut.parse(document).sourceType()).isEqualTo(SourceType.PDF);
    }

    @Test
    void neverSwallowsParserFailures() {
        DocumentParser broken = document -> {
            throw new IllegalStateException("boom");
        };
        var sut = new FormatAwareDocumentParser(Map.of(SourceType.PDF, broken), fallback);
        var document = new Document("d1", "", Map.of(FORMAT, "PDF"));
        assertThatThrownBy(() -> sut.parse(document))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}
