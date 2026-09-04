package com.rag.basic;

import com.rag.basic.api.IngestionController;
import com.rag.basic.services.RetrievalService;
import com.rag.common.services.EmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the rag-basic application context boots without external services and
 * that the full ingestion-retrieval pipeline works end-to-end. The vector store
 * is switched to the in-memory provider ({@code simple}) and the domain
 * {@link EmbeddingModel} is mocked with deterministic stubs so no external
 * services are required.
 */
@SpringBootTest(classes = RagBasicApplication.class,
        properties = {
                "rag.vector-store.type=simple",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"
        })
@AutoConfigureMockMvc
class RagBasicApplicationContextTest {

    @MockitoBean
    private EmbeddingModel domainEmbeddingModel;

    @Autowired private MockMvc mockMvc;
    @Autowired private IngestionController ingestionController;
    @Autowired private RetrievalService retrievalService;

    @BeforeEach
    void stubDeterministicEmbeddings() {
        when(domainEmbeddingModel.embed(anyString())).thenAnswer(inv -> {
            String text = inv.getArgument(0, String.class);
            return toEmbeddingList(text);
        });
    }

    private static List<Float> toEmbeddingList(String text) {
        float[] vec = new float[text.length()];
        for (int i = 0; i < text.length(); i++) {
            vec[i] = (float) (text.charAt(i) % 7);
        }
        var result = new java.util.ArrayList<Float>(vec.length);
        for (float v : vec) result.add(v);
        return result;
    }

    @Test
    void contextLoads() {
        assertThat(ingestionController).isNotNull();
        assertThat(retrievalService).isNotNull();
    }

    @Test
    void shouldIngestTextAndRetrieveRelevantChunks() throws Exception {
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
                                {"query": "domain-driven design strategic modeling", "topK": 3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].content").isNotEmpty());
    }
}
