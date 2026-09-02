package com.rag.memory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the rag-memory application context boots without external services. The
 * test resources override the Postgres datasource with an in-memory H2 database
 * (PostgreSQL compatibility mode) so no live database is required.
 */
@SpringBootTest(classes = RagMemoryApplication.class)
class RagMemoryApplicationContextTest {

    @Test
    void contextLoads() {
        // no-op: the Spring context booting successfully is the assertion
    }
}
