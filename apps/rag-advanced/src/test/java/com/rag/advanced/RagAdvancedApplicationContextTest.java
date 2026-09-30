package com.rag.advanced;

import com.rag.common.ingestion.web.IngestionController;
import com.rag.advanced.services.AdvancedRetrievalService;
import com.rag.common.core.services.EmbeddingModelPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the rag-advanced application context boots without external services
 * and that the full ingestion-advanced-retrieval pipeline works end-to-end. The
 * vector store is switched to the in-memory provider and the domain
 * {@link EmbeddingModelPort} is mocked with deterministic stubs so no external
 * services are required.
 */
@SpringBootTest(classes = RagAdvancedApplication.class,
        properties = {
                "rag.vector-store.type=simple",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"
        })
@AutoConfigureMockMvc
class RagAdvancedApplicationContextTest {

    @MockitoBean
    private EmbeddingModelPort domainEmbeddingModel;

    @Autowired private MockMvc mockMvc;
    @Autowired private IngestionController ingestionController;
    @Autowired private AdvancedRetrievalService retrievalService;

    @BeforeEach
    void stubDeterministicEmbeddings() {
        when(domainEmbeddingModel.embed(anyString())).thenAnswer(inv -> {
            var text = inv.getArgument(0, String.class);
            return toEmbeddingList(text);
        });
        when(domainEmbeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(RagAdvancedApplicationContextTest::toEmbeddingList).toList();
        });
    }

    private static List<Float> toEmbeddingList(String text) {
        float[] vec = new float[text.length()];
        for (int i = 0; i < text.length(); i++) {
            vec[i] = (float) (text.charAt(i) % 7);
        }
        var result = new ArrayList<Float>(vec.length);
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
                                    "content": "Azure virtual machines scale out \
                                    the compute capacity of a cloud workload by \
                                    automating the provisioning of identical nodes.",
                                    "metadata": {"title": "Azure VM Overview"}
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.chunkCount").isNumber());
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("Azure VM Overview"));
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query": "azure virtual machines", "topK": 3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].content").isNotEmpty());
        mockMvc.perform(post("/api/query/advanced")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "azure virtual machines", "topK": 3,
                                 "metadata": {"title": "Azure VM Overview"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1));
    }
}
