package com.rag.advanced.retrieval;

import com.rag.common.core.domain.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalScorerTest {

    private final LexicalScorer sut = new LexicalScorer();

    private static Chunk chunk(String content) {
        return new Chunk("id", "doc", content, 0, Map.of());
    }

    @Test
    void scoresZeroForEmptyQueryOrCandidates() {
        assertThat(sut.score("", List.of(chunk("text")))).containsExactly(0.0d);
        assertThat(sut.score("query", List.of())).isEmpty();
    }

    @Test
    void rewardsTermFrequency() {
        var once = chunk("conductor measures current");
        var thrice = chunk("conductor conductor conductor");
        var scores = sut.score("conductor", List.of(once, thrice));
        assertThat(scores[1]).isGreaterThan(scores[0]);
    }

    @Test
    void punishesDocumentsWithoutTheTerm() {
        var match = chunk("conductor");
        var miss = chunk("unrelated text without any query terms");
        var scores = sut.score("conductor", List.of(match, miss));
        assertThat(scores[0]).isGreaterThan(scores[1]);
        assertThat(scores[1]).isZero();
    }

    @Test
    void matchesTokensCaseInsensitively() {
        var scores = sut.score("CONDUCTOR", List.of(chunk("Conductor conductive")));
        assertThat(scores[0]).isGreaterThan(0.0d);
    }

    @Test
    void unmatchedQueryTermsContributeNothing() {
        var scores = sut.score("missing term", List.of(chunk("conductor")));
        assertThat(scores[0]).isZero();
    }

    @Test
    void rarerQueryTermWeighsMore() {
        var docs = List.of(chunk("cobalt conductor"), chunk("cobalt cobalt cobalt"));
        var scores = sut.score("cobalt conductor", docs);
        assertThat(scores[0]).isGreaterThan(scores[1]);
    }
}