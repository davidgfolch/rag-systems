package com.rag.tui;

import com.rag.tui.ui.CommandDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the rag-tui application context boots as a non-web CLI: with
 * {@code web-application-type: none} no embedded web server is started. The
 * interactive {@link ApplicationRunner} is replaced by a no-op mock so the test
 * does not block on stdin while the context loads, which means the runner's own
 * dependencies are never resolved here - so ask for the dispatcher directly to
 * keep {@code tuiRunner(CommandDispatcher, ...)} wiring honest.
 */
@SpringBootTest(classes = RagTuiApplication.class)
class RagTuiApplicationContextTest {

    @SuppressWarnings("unused")
    @MockitoBean
    private ApplicationRunner tuiRunner;

    @Autowired
    private ApplicationContext context;

    @Test
    void wiresTheDispatcherTheInteractiveRunnerRequires() {
        assertThat(context.getBean(CommandDispatcher.class)).isNotNull();
    }
}
