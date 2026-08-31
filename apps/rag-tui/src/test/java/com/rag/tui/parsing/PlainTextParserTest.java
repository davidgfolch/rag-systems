package com.rag.tui.parsing;

import com.rag.common.domain.Document;
import com.rag.common.services.DocumentParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextParserTest {

    private final DocumentParser sut = new PlainTextParser();

    @Test
    void returnsContentAsIs() {
        Document doc = new Document("d1", "plain text content", Map.of());
        assertThat(sut.parse(doc)).isEqualTo("plain text content");
    }
}
