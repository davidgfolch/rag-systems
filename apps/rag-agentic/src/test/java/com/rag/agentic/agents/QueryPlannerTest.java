package com.rag.agentic.agents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPlannerTest {

    private final QueryPlanner sut = new QueryPlanner();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'What is RAG?', 'What is RAG'",
            "'Deploy on k8s. Monitor on prometheus', 'Deploy on k8s;Monitor on prometheus'",
            "'chunk and embed and retrieve', 'chunk;embed;retrieve'"
    })
    void splitsCompoundQuestions(String question, String expectedPlan) {
        assertThat(sut.plan(question)).containsExactly(expectedPlan.split(";"));
    }

    @Test
    void keepsSingleQuestionIntact() {
        assertThat(sut.plan("Explain hybrid search")).containsExactly("Explain hybrid search");
    }

    @Test
    void returnsEmptyForBlank() {
        assertThat(sut.plan("   ")).isEmpty();
        assertThat(sut.plan(null)).isEmpty();
    }

    @Test
    void deduplicatesRepeatedSubQueries() {
        assertThat(sut.plan("what?what")).containsExactly("what");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(sut.plan("  alpha beta  ")).containsExactly("alpha beta");
    }
}