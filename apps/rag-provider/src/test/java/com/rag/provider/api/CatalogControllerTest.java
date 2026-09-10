package com.rag.provider.api;

import com.rag.contract.provider.ModelCapabilitiesDTO;
import com.rag.contract.provider.ModelCatalogDTO;
import com.rag.contract.provider.ModelCostDTO;
import com.rag.contract.provider.ModelLimitsDTO;
import com.rag.contract.provider.ProviderModelDTO;
import com.rag.provider.services.ModelCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock private ModelCatalogService catalogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogController(catalogService)).build();
    }

    @Test
    void shouldReturnCachedCatalog() throws Exception {
        when(catalogService.catalog()).thenReturn(catalog());
        mockMvc.perform(get("/api/provider/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].providerId").value("ollama"))
                .andExpect(jsonPath("$.models[0].modelId").value("phi4"))
                .andExpect(jsonPath("$.models[0].name").value("Phi 4"))
                .andExpect(jsonPath("$.models[0].limits.context").value(16000))
                .andExpect(jsonPath("$.models[0].capabilities.reasoning").value(true))
                .andExpect(jsonPath("$.models[0].cost.inputPerMillion").value(0.0))
                .andExpect(jsonPath("$.source").value("https://models.dev/api.json"))
                .andExpect(jsonPath("$.fetchedAt").exists());
    }

    @Test
    void shouldRefreshCatalogOnDemand() throws Exception {
        when(catalogService.refresh()).thenReturn(catalog());
        mockMvc.perform(post("/api/provider/catalog/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].providerId").value("ollama"));
        verify(catalogService).refresh();
    }

    private static ModelCatalogDTO catalog() {
        var model = new ProviderModelDTO("ollama", "phi4", "Phi 4",
                new ModelLimitsDTO(16000, 0),
                new ModelCapabilitiesDTO(true, false, true),
                new ModelCostDTO(0, 0), null);
        return new ModelCatalogDTO(List.of(model), "https://models.dev/api.json",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}