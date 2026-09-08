package com.rag.provider.api;

import com.rag.contract.provider.ConfigureProviderRequest;
import com.rag.contract.provider.ModelSpecDTO;
import com.rag.contract.provider.ProviderStatusDTO;
import com.rag.provider.services.ModelCatalogService;
import com.rag.provider.services.ModelRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProviderControllerTest {

    @Mock private ModelRouter router;
    @Mock private ModelCatalogService catalogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProviderController(router, catalogService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnStatus() throws Exception {
        when(router.status()).thenReturn(new ProviderStatusDTO(
                new ModelSpecDTO("ollama", "phi4"),
                new ModelSpecDTO("ollama", "nomic-embed-text"),
                "ollama", 768));

        mockMvc.perform(get("/api/provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.chat.model").value("phi4"))
                .andExpect(jsonPath("$.embeddingDimension").value(768));
    }

    @Test
    void shouldSwitchChatModel() throws Exception {
        when(catalogService.isKnown("ollama", "qwen3")).thenReturn(true);

        mockMvc.perform(post("/api/provider/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":\"ollama\",\"model\":\"qwen3\"}"))
                .andExpect(status().isNoContent());

        verify(router).switchChat(new ModelSpecDTO("ollama", "qwen3"));
    }

    @Test
    void shouldStillSwitchChatModelNotInCatalog() throws Exception {
        when(catalogService.isKnown("ollama", "custom-model")).thenReturn(false);

        mockMvc.perform(post("/api/provider/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":\"ollama\",\"model\":\"custom-model\"}"))
                .andExpect(status().isNoContent());

        verify(router).switchChat(new ModelSpecDTO("ollama", "custom-model"));
    }

    @Test
    void shouldStillSwitchEmbeddingModelNotInCatalog() throws Exception {
        when(catalogService.isKnown("ollama", "local-embed")).thenReturn(false);

        mockMvc.perform(post("/api/provider/embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":\"ollama\",\"model\":\"local-embed\"}"))
                .andExpect(status().isNoContent());

        verify(router).switchEmbedding(new ModelSpecDTO("ollama", "local-embed"));
    }

    @Test
    void shouldSwitchEmbeddingModel() throws Exception {
        when(catalogService.isKnown("ollama", "nomic-embed-text")).thenReturn(true);

        mockMvc.perform(post("/api/provider/embedding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":\"ollama\",\"model\":\"nomic-embed-text\"}"))
                .andExpect(status().isNoContent());

        verify(router).switchEmbedding(new ModelSpecDTO("ollama", "nomic-embed-text"));
    }

    @Test
    void shouldConfigureProvider() throws Exception {
        when(router.status()).thenReturn(new ProviderStatusDTO(
                new ModelSpecDTO("glhf", "some-model"),
                new ModelSpecDTO("ollama", "nomic-embed-text"),
                "glhf", -1));

        mockMvc.perform(post("/api/provider/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":\"glhf\",\"type\":\"OPENAI_COMPATIBLE\"," +
                                "\"displayName\":\"GLHF\",\"baseUrl\":\"https://glhf.chat\",\"apiKey\":\"k\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("glhf"));

        verify(router).configure(new ConfigureProviderRequest("glhf", "OPENAI_COMPATIBLE",
                "GLHF", "https://glhf.chat", "k"));
    }

    @Test
    void shouldMapBadRequestTo400() throws Exception {
        doThrow(new IllegalArgumentException("Unknown provider: nope"))
                .when(router).switchChat(any(ModelSpecDTO.class));

        mockMvc.perform(post("/api/provider/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerId\":\"nope\",\"model\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unknown provider: nope"));
    }
}