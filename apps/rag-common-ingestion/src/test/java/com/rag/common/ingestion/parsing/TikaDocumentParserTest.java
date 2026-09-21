package com.rag.common.ingestion.parsing;

import com.rag.common.core.domain.Document;
import com.rag.common.core.domain.MetadataKeys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    @Test
    void fallsBackToContentWhenNoRawBytes() {
        Document doc = new Document("d1", "no raw data", Map.of());
        assertThat(parser.parse(doc)).isEqualTo("no raw data");
    }

    @Test
    void extractsTextFromHtml() {
        String html = "<html><body><h1>Title</h1><p>Some <b>content</b> here.</p></body></html>";
        Document doc = new Document("d1", "", Map.of(MetadataKeys.RAW_BYTES, html.getBytes(StandardCharsets.UTF_8)));
        String result = parser.parse(doc);
        assertThat(result)
                .containsIgnoringCase("Title")
                .containsIgnoringCase("Some content here");
    }

    @Test
    void extractsTextFromBase64RawForBackwardCompatibility() {
        String html = "<html><body><p>legacy base64</p></body></html>";
        Document doc = new Document("d1", "", Map.of(MetadataKeys.RAW, base64(html)));
        assertThat(parser.parse(doc)).containsIgnoringCase("legacy base64");
    }

    @Test
    void throwsWhenRawIsNotString() {
        Document doc = new Document("d1", "", Map.of(MetadataKeys.RAW, 123));
        assertThat(parser.parse(doc)).isEmpty();
    }

    @Test
    void throwsOnInvalidBase64LegacyRaw() {
        Document doc = new Document("d1", "", Map.of(MetadataKeys.RAW, "!!not-base64!!"));
        assertThatThrownBy(() -> parser.parse(doc)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrapsCorruptBinaryParseFailure() {
        String corruptPdf = """
                %PDF-1.4
                1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
                xref
                0 1
                0000000000 65535 f
                trailer<</Size 1/Root 1 0 R>>
                startxref
                99999999
                %%EOF""";
        Document doc = new Document("d1", "", Map.of(MetadataKeys.RAW_BYTES, corruptPdf.getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> parser.parse(doc))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse document d1");
    }

    private static String base64(String value) {
        return java.util.Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}