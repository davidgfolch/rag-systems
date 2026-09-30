package com.rag.agentic;

import com.rag.common.ingestion.web.IngestionController;
import com.rag.agentic.orchestration.AgenticChatService;
import com.rag.agentic.services.AgenticRetrievalService;
import com.rag.common.core.services.EmbeddingModelPort;
import com.rag.common.generation.adapter.RemoteChatModelPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the rag-agentic application context boots without external services and
 * that the ingestion-agentic-retrieval pipeline works end-to-end. The vector
 * store is switched to the in-memory provider, the domain
 * {@link EmbeddingModelPort} and the Spring AI {@link EmbeddingModel} are mocked
 * with deterministic stubs, and chat model ports are mocked so the loop takes
 * its deterministic search fallback. No external services are required.
 */
@SpringBootTest(classes = RagAgenticApplication.class,
        properties = {
                "rag.vector-store.type=simple",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration"
        })
@AutoConfigureMockMvc
class RagAgenticApplicationContextTest {

    @MockitoBean
    private EmbeddingModelPort domainEmbeddingModel;

    @MockitoBean
    private EmbeddingModel springAiEmbeddingModel;

    @MockitoBean
    private RemoteChatModelPort remoteChatModelPort;

    @Autowired private MockMvc mockMvc;
    @Autowired private IngestionController ingestionController;
    @Autowired private AgenticRetrievalService retrievalService;
    @Autowired private AgenticChatService agenticChatService;

    @BeforeEach
    void stubDeterministicBehaviors() {
        when(remoteChatModelPort.complete(anyString())).thenReturn("");
        when(domainEmbeddingModel.embed(anyString())).thenAnswer(inv -> {
            var text = inv.getArgument(0, String.class);
            return toEmbeddingList(text);
        });
        when(domainEmbeddingModel.embed(anyList())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            return texts.stream().map(RagAgenticApplicationContextTest::toEmbeddingList).toList();
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
        assertThat(agenticChatService).isNotNull();
    }

    @Test
    void shouldIngestTextAndRunAgenticPipeline() throws Exception {
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
        mockMvc.perform(post("/api/query/agentic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "azure virtual machines", "topK": 3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").isArray())
                .andExpect(jsonPath("$.steps.length()").value(1));
    }
}
