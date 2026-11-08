package com.rag.common.retrieval.store;

import com.rag.common.core.domain.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentDeduplicatorTest {

    @ParameterizedTest(name = "fetchSize({0}) = {1}")
    @CsvSource({
            "0, 0",
            "-3, 0",
            "1, 4",
            "2, 8",
            "64, 256",
            "100, 256",
            "300, 300"
    })
    void scalesTheFetchWindowAndCapsIt(int topK, int expected) {
        assertThat(ContentDeduplicator.fetchSize(topK)).isEqualTo(expected);
    }

    @Test
    void keepsOnlyTheFirstOccurrenceOfEachNormalizedContent() {
        var first = chunk("c1", "Kafka Streams");
        var duplicate = chunk("c2", "  kafka   streams ");
        var other = chunk("c3", "Kafka Connect");

        var distinct = ContentDeduplicator.distinct(List.of(first, duplicate, other), 10);

        assertThat(distinct).extracting(Chunk::getId).containsExactly("c1", "c3");
    }

    @Test
    void truncatesToTheRequestedTopK() {
        var hits = List.of(chunk("c1", "one"), chunk("c2", "two"), chunk("c3", "three"));

        assertThat(ContentDeduplicator.distinct(hits, 2)).extracting(Chunk::getId)
                .containsExactly("c1", "c2");
    }

    @Test
    void treatsBlankContentsAsOneGroup() {
        var hits = List.of(chunk("c1", ""), chunk("c2", "   "), chunk("c3", "real"));

        assertThat(ContentDeduplicator.distinct(hits, 10)).extracting(Chunk::getId)
                .containsExactly("c1", "c3");
    }

    @Test
    void returnsEmptyForInvalidArguments() {
        assertThat(ContentDeduplicator.distinct(null, 3)).isEmpty();
        assertThat(ContentDeduplicator.distinct(List.of(), 3)).isEmpty();
        assertThat(ContentDeduplicator.distinct(List.of(chunk("c1", "one")), 0)).isEmpty();
    }

    private static Chunk chunk(String id, String content) {
        return new Chunk(id, "d1", content, 0, Map.of());
    }
}
