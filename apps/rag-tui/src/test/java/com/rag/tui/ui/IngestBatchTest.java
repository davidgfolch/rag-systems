package com.rag.tui.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestBatchTest {

    private final List<String> tokens = new ArrayList<>();
    private final IngestBatch sut = new IngestBatch("Crawl ingestion", tokens::add);

    @Test
    void shouldEmitSummaryOnceClosedAndDrained() {
        sut.submitted();
        sut.completed(3);
        assertThat(tokens).isEmpty();
        sut.close();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0))
                .contains("Crawl ingestion")
                .contains("1 page(s) complete")
                .contains("0 failed")
                .contains("3 chunk(s) total");
    }

    @Test
    void shouldWaitForEveryJobBeforeSummarising() {
        sut.submitted();
        sut.submitted();
        sut.completed(3);
        sut.close();
        assertThat(tokens).isEmpty();
        sut.completed(4);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0)).contains("2 page(s) complete").contains("7 chunk(s) total");
    }

    @Test
    void shouldReportFailuresWithTheirJobLabel() {
        sut.submitted();
        sut.submitted();
        sut.completed(5);
        sut.failed("page https://x.com/a", "boom");
        sut.close();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0))
                .contains("1 page(s) complete")
                .contains("1 failed")
                .contains("page https://x.com/a: boom");
    }

    @Test
    void shouldStaySilentWhenNoJobWasSubmitted() {
        sut.close();
        assertThat(tokens).isEmpty();
    }

    @Test
    void shouldNotSummariseTwice() {
        sut.submitted();
        sut.completed(1);
        sut.close();
        sut.completed(2);
        assertThat(tokens).hasSize(1);
    }
}
