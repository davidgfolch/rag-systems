package com.rag.agentic.agents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionAgentTest {

    private final ReflectionAgent sut = new ReflectionAgent();

    @Test
    void continuesWhenNoContextAndBudgetRemains() {
        assertThat(sut.shouldContinue(false, 1, 3)).isTrue();
    }

    @Test
    void answersWhenContextGathered() {
        assertThat(sut.shouldContinue(true, 0, 3)).isFalse();
    }

    @Test
    void stopsWhenBudgetExhaustedWithoutContext() {
        assertThat(sut.shouldContinue(false, 3, 3)).isFalse();
    }

    @Test
    void stopsAtExactBudgetBoundary() {
        assertThat(sut.shouldContinue(false, 2, 3)).isTrue();
    }
}