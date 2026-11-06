package com.rag.common.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceLocationTest {

    @Test
    void noneHasNoProvenance() {
        assertThat(SourceLocation.NONE.isEmpty()).isTrue();
        assertThat(SourceLocation.NONE.page()).isNull();
        assertThat(SourceLocation.NONE.domPath()).isNull();
        assertThat(SourceLocation.NONE.jsonPath()).isNull();
        assertThat(SourceLocation.NONE.startOffset()).isNull();
        assertThat(SourceLocation.NONE.endOffset()).isNull();
    }

    @Test
    void anyFieldMakesTheLocationNonEmpty() {
        assertThat(new SourceLocation(1, null, null, null, null).isEmpty()).isFalse();
        assertThat(new SourceLocation(null, "p", null, null, null).isEmpty()).isFalse();
        assertThat(new SourceLocation(null, null, "$", null, null).isEmpty()).isFalse();
        assertThat(new SourceLocation(null, null, null, 0, null).isEmpty()).isFalse();
        assertThat(new SourceLocation(null, null, null, null, 5).isEmpty()).isFalse();
    }

    @Test
    void comparesByValue() {
        var location = new SourceLocation(1, "h1", "$.a", 0, 10);
        var same = new SourceLocation(1, "h1", "$.a", 0, 10);
        assertThat(location).isEqualTo(same).hasSameHashCodeAs(same)
                .isNotEqualTo(SourceLocation.NONE);
        assertThat(location.toString()).contains("h1");
    }
}
