package com.rag.tui;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves the rag-tui application context boots as a non-web CLI: with
 * {@code web-application-type: none} no embedded web server is started. The
 * interactive {@link ApplicationRunner} is replaced by a no-op mock so the test
 * does not block on stdin while the context loads.
 */
@SpringBootTest(classes = RagTuiApplication.class)
class RagTuiApplicationContextTest {

    @SuppressWarnings("unused")
    @MockitoBean
    private ApplicationRunner tuiRunner;

    @Test
    void contextLoads() {
        // no-op: the Spring context booting successfully is the assertion
    }
}
