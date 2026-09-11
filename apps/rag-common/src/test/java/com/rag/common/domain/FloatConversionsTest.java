package com.rag.common.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FloatConversionsTest {

    @Test
    void convertsFloatArrayToFloatList() {
        var result = FloatConversions.toFloatList(new float[]{1.5f, 2.0f, -0.5f});
        assertThat(result).containsExactly(1.5f, 2.0f, -0.5f);
    }

    @Test
    void convertsFloatListToFloatArray() {
        var result = FloatConversions.toFloatArray(List.of(1.5f, 2.0f, -0.5f));
        assertThat(result).containsExactly(1.5f, 2.0f, -0.5f);
    }

    @Test
    void handlesEmptyArray() {
        assertThat(FloatConversions.toFloatList(new float[]{})).isEmpty();
    }

    @Test
    void handlesEmptyList() {
        assertThat(FloatConversions.toFloatArray(List.of())).isEmpty();
    }
}
