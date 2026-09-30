package com.rag.agentic.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.agentic.domain.AgenticConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallParserTest {

    private final ToolCallParser sut = new ToolCallParser(new ObjectMapper());

    @Test
    void parsesSingleToolCallObject() {
        var calls = sut.parse("""
                {"action":"tool","tool":"document_search","args":{
                    "query":"fast retrieval","documentId":"doc-1","topK":2}}""");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("document_search");
        assertThat(calls.get(0).query()).isEqualTo("fast retrieval");
        assertThat(calls.get(0).documentId()).isEqualTo("doc-1");
        assertThat(calls.get(0).topK()).isEqualTo(2);
    }

    @Test
    void parsesJsonArrayEmbeddedInProse() {
        var calls = sut.parse("""
                Here is the plan:

                [{"action":"tool","tool":"search","args":{"query":"one"}},{"action":"tool","tool":"web_search","args":{"url":"https://x.io"}}]""");
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).name()).isEqualTo("search");
        assertThat(calls.get(1).name()).isEqualTo("web_search");
        assertThat(calls.get(1).url()).isEqualTo("https://x.io");
    }

    @Test
    void handlesTopLevelFieldsWithoutArgs() {
        var calls = sut.parse("{\"tool\":\"search\",\"query\":\"hybrid\",\"topK\":7}");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("search");
        assertThat(calls.get(0).query()).isEqualTo("hybrid");
        assertThat(calls.get(0).topK()).isEqualTo(7);
    }

    @Test
    void stringsWithBracesDoNotBreakExtraction() {
        var calls = sut.parse("""
                {"action":"tool","tool":"search","args":{"query":"log {java} braces"}}""");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).query()).isEqualTo("log {java} braces");
    }

    @Test
    void skipsAnswerAction() {
        assertThat(sut.parse("{\"action\":\"answer\"}")).isEmpty();
    }

    @Test
    void skipsMalformedOrEmptyBlocks() {
        assertThat(sut.parse("no json here")).isEmpty();
        assertThat(sut.parse("null")).isEmpty();
        assertThat(sut.parse("{\"unknown\":true}")).isEmpty();
        assertThat(sut.parse(null)).isEmpty();
        assertThat(sut.parse("   ")).isEmpty();
    }

    @Test
    void topKAsStringParsesToInt() {
        var calls = sut.parse("{\"tool\":\"search\",\"args\":{\"query\":\"q\",\"topK\":\"3\"}}");
        assertThat(calls.get(0).topK()).isEqualTo(3);
    }

    @Test
    void topKAsGarbageFallsBackToUnspecified() {
        var calls = sut.parse("{\"tool\":\"search\",\"args\":{\"query\":\"q\",\"topK\":\"abc\"}}");
        assertThat(calls.get(0).topK()).isEqualTo(AgenticConstants.UNSPECIFIED_TOP_K);
    }

    @Test
    void skipsSingleQuotedJson() {
        assertThat(sut.parse("{'tool':'search','args':{'query':'quoted'}}")).isEmpty();
    }
}