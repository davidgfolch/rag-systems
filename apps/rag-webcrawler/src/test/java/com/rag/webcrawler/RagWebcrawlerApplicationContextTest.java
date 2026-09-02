package com.rag.webcrawler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the rag-webcrawler application context boots with no external
 * dependencies (no DB, no LLM, no blocking runners).
 */
@SpringBootTest(classes = RagWebcrawlerApplication.class)
class RagWebcrawlerApplicationContextTest {

    @Test
    void contextLoads() {
        // no-op: the Spring context booting successfully is the assertion
    }
}
