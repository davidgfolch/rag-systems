package com.rag.basic.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class JacksonConfig {

    private static final int MAX_STRING_LENGTH = 50 * 1024 * 1024;

    @Bean
    Jackson2ObjectMapperBuilderCustomizer streamReadConstraintsCustomizer() {
        return builder -> builder.postConfigurer(
                mapper -> mapper.getFactory().setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxStringLength(MAX_STRING_LENGTH)
                                .build()));
    }
}
