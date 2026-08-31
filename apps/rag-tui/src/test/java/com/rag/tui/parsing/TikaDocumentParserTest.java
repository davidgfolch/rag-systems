package com.rag.tui.parsing;

import com.rag.common.domain.Document;
import com.rag.common.services.DocumentParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TikaDocumentParserTest {

    private final DocumentParser sut = new TikaDocumentParser();

    @Test
    void returnsContentWhenNoRawMetadata() {
        Document doc = new Document("d1", "fallback", Map.of());
        assertThat(sut.parse(doc)).isEqualTo("fallback");
    }

    @Test
    void extractsTextFromRawHtml() {
        Document doc = new Document("d1", "ignored", Map.of("raw",
                "<html><body><h1>Title</h1><p>Hello world.</p></body></html>"));
        String parsed = sut.parse(doc);
        assertThat(parsed).contains("Hello world");
        assertThat(parsed).doesNotContain("<p>");
    }
}
