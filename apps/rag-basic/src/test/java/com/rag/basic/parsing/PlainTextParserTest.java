package com.rag.basic.parsing;

import com.rag.common.domain.Document;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextParserTest {

    private final PlainTextParser parser = new PlainTextParser();

    @Test
    void returnsContentAsIs() {
        Document doc = new Document("d1", "Hello plain text", Map.of());
        assertThat(parser.parse(doc)).isEqualTo("Hello plain text");
    }
}