package com.rag.basic;

import com.rag.basic.services.RetrievalService;
import com.rag.common.testcontainers.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the real PostgreSQL + pgvector path. Boots the full
 * rag-basic context against a Testcontainers-backed pgvector container (shared
 * via {@link PostgresContainerConfig}) and exercises ingest -> PgVector store ->
 * retrieve. The embedding model is a deterministic stub so no LLM is contacted,
 * but all persistence and vector similarity run against real PostgreSQL.
 */
@ContextConfiguration(initializers = PostgresContainerConfig.class)
@SpringBootTest(classes = RagBasicApplication.class,
        properties = {
                "rag.vector-store.type=pgvector",
                "spring.ai.vectorstore.pgvector.schema-name=rag_basic",
                "spring.ai.vectorstore.pgvector.table-name=chunks",
                "spring.ai.vectorstore.pgvector.initialize-schema=true"
        })
@Import(RagBasicPostgresIntegrationTest.DeterministicEmbeddingConfig.class)
@AutoConfigureMockMvc
class RagBasicPostgresIntegrationTest {

    static final int DIM = 64;

    @Autowired private MockMvc mockMvc;
    @Autowired private RetrievalService retrievalService;

    @Test
    void contextLoads() {
        assertThat(retrievalService).isNotNull();
    }

    @Test
    void shouldIngestToPgVectorAndRetrieveRelevantChunks() throws Exception {
        mockMvc.perform(post("/api/documents/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "content": "Domain-Driven Design focuses on \
                                    strategically designing complex software systems \
                                    by centering the model on the business domain.",
                                    "metadata": {"title": "DDD Overview"}
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.chunkCount").isNumber());
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("DDD Overview"));
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "domain-driven design strategic modeling", "topK": 3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].content").isNotEmpty());
    }

    /**
     * Deterministic, fixed-dimension embedding provider so the pgvector store
     * can build/query a vector(64) column without any real LLM. Docs sharing
     * characters with the query score highly (char-frequency histogram).
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicEmbeddingConfig {

        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new CharHashEmbeddingModel();
        }
    }

    private static final class CharHashEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            var results = request.getInstructions().stream()
                    .map(text -> new Embedding(embedText(text), 0))
                    .toList();
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(Document document) {
            return embedText(document.getText());
        }

        private static float[] embedText(String text) {
            var vec = new float[DIM];
            text.toLowerCase().chars().forEach(c -> vec[Math.floorMod(c, DIM)] += 1f);
            return vec;
        }
    }
}