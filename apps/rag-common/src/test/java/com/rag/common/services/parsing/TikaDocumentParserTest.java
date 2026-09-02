package com.rag.common.services.parsing;

import com.rag.common.domain.Document;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        Document doc = new Document("d1", "", Map.of("rawBytes", html.getBytes(StandardCharsets.UTF_8)));

        String result = parser.parse(doc);

        assertThat(result)
                .containsIgnoringCase("Title")
                .containsIgnoringCase("Some content here");
    }

    @Test
    void extractsTextFromBase64RawForBackwardCompatibility() {
        String html = "<html><body><p>legacy base64</p></body></html>";
        Document doc = new Document("d1", "", Map.of("raw", base64(html)));

        assertThat(parser.parse(doc)).containsIgnoringCase("legacy base64");
    }

    @Test
    void throwsWhenRawIsNotString() {
        Document doc = new Document("d1", "", Map.of("raw", 123));
        assertThat(parser.parse(doc)).isEmpty();
    }

    private static String base64(String value) {
        return java.util.Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}