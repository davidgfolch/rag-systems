package com.rag.basic.parsing;

import com.rag.common.domain.Document;
import org.junit.jupiter.api.Test;

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
        Document doc = new Document("d1", "", Map.of("raw", html));

        String result = parser.parse(doc);

        assertThat(result).containsIgnoringCase("Title");
        assertThat(result).containsIgnoringCase("Some content here");
    }

    @Test
    void throwsWhenRawIsNotString() {
        Document doc = new Document("d1", "", Map.of("raw", 123));
        assertThat(parser.parse(doc)).isEmpty();
    }
}