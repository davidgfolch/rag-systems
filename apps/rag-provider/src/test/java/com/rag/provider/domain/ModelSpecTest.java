package com.rag.provider.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelSpecTest {

    @ParameterizedTest(name = "should reject providerId when providerId is \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectInvalidProviderId(String providerId) {
        assertThatThrownBy(() -> new ModelSpec(providerId, "phi4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("providerId must not be blank");
    }

    @ParameterizedTest(name = "should reject model when model is \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectInvalidModel(String model) {
        assertThatThrownBy(() -> new ModelSpec("ollama", model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("model must not be blank");
    }

    @Test
    void shouldKeepProvidedValuesWhenValid() {
        var spec = new ModelSpec("ollama", "phi4");
        assertThat(spec.providerId()).isEqualTo("ollama");
        assertThat(spec.model()).isEqualTo("phi4");
    }
}
