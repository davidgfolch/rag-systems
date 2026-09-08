package com.rag.provider;

import com.rag.provider.services.ModelRouter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the rag-provider application context boots without external services.
 * Cold-starting the router only builds clients (no network I/O), so booting is
 * fully offline.
 */
@SpringBootTest(classes = RagProviderApplication.class)
class RagProviderApplicationContextTest {

    @Autowired private ModelRouter router;

    @Test
    void contextLoads() {
        assertThat(router).isNotNull();
        assertThat(router.currentChat()).isNotNull();
        assertThat(router.currentEmbedding()).isNotNull();
        assertThat(router.chatModel()).isNotNull();
        assertThat(router.embeddingModel()).isNotNull();
    }
}