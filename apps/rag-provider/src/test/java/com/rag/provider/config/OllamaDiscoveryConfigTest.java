package com.rag.provider.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaDiscoveryConfigTest {

    @ParameterizedTest(name = "should return no subnets for \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldReturnNoSubnetsWhenUnset(String value) {
        assertThat(OllamaDiscoveryConfig.parseSubnets(value)).isEmpty();
    }

    @Test
    void shouldSplitTrimAndDropEmptySubnets() {
        assertThat(OllamaDiscoveryConfig.parseSubnets(" 192.168.1.0/24 , 10.0.0.0/8 ,, "))
                .isEqualTo(List.of("192.168.1.0/24", "10.0.0.0/8"));
    }

    @Test
    void shouldKeepSingleSubnet() {
        assertThat(OllamaDiscoveryConfig.parseSubnets("192.168.7.0/24"))
                .containsExactly("192.168.7.0/24");
    }
}
