package com.rag.basic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the rag-basic application context boots without external services: the
 * vector store type is switched to the in-memory provider ({@code simple}) so no
 * Postgres connection is attempted, and Ollama/OpenAI model beans are created
 * lazily without connecting.
 */
@SpringBootTest(classes = RagBasicApplication.class,
        properties = {
                "rag.vector-store.type=simple",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"
        })
class RagBasicApplicationContextTest {

    @Test
    void contextLoads() {
        // no-op: the Spring context booting successfully is the assertion
    }
}
