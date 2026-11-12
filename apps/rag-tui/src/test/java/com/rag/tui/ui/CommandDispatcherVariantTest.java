package com.rag.tui.ui;

import com.rag.contract.pipeline.PipelineVariantDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static com.rag.tui.testfixture.TestModules.BASIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherVariantTest extends AbstractCommandDispatcherTest {

    @Test
    void listsVariantsAndActivatesThePickedOne() {
        var recursive = variant("v1", "RECURSIVE", 512, 128);
        when(apiClient.variants(BASIC)).thenReturn(List.of(recursive));
        when(prompter.pick(eq("Switch pipeline variant"), anyList())).thenReturn(Optional.of("v1"));
        when(apiClient.activateVariant(BASIC, "v1")).thenReturn(recursive);
        var result = handle("variant");
        assertThat(result).contains("Variant active on " + BASIC, "RECURSIVE", "512/128", "768d");
        verify(apiClient).activateVariant(BASIC, "v1");
    }

    @Test
    void activatesTheVariantIdPassedAsArgument() {
        var token = variant("v2", "TOKEN", 256, 64);
        when(apiClient.variants(BASIC)).thenReturn(List.of(token));
        when(apiClient.activateVariant(BASIC, "v2")).thenReturn(token);
        assertThat(handle("variant v2")).contains("Variant active on " + BASIC, "TOKEN", "256/64");
    }

    @Test
    void reportsUnknownVariantWithoutCallingTheModule() {
        when(apiClient.variants(BASIC)).thenReturn(List.of(variant("v1", "RECURSIVE", 512, 128)));
        assertThat(handle("variant v9")).contains("Unknown variant: v9");
        verify(apiClient, never()).activateVariant(anyString(), anyString());
    }

    @Test
    void pointsAtTheRegistrationEndpointWhenNoVariantExists() {
        when(apiClient.variants(BASIC)).thenReturn(List.of());
        assertThat(handle("variant"))
                .contains("No pipeline variants", "/api/modules/" + BASIC + "/variants");
    }

    @Test
    void cancelledPickerKeepsTheCurrentVariant() {
        when(apiClient.variants(BASIC)).thenReturn(List.of(variant("v1", "RECURSIVE", 512, 128)));
        when(prompter.pick(eq("Switch pipeline variant"), anyList())).thenReturn(Optional.empty());
        assertThat(handle("variant")).isEmpty();
        verify(apiClient, never()).activateVariant(anyString(), anyString());
    }

    @Test
    void createsVariantFromExplicitStrategyAndWindow() {
        when(apiClient.activeVariant(BASIC)).thenReturn(Optional.of(variant("v1", "RECURSIVE", 512, 128)));
        when(apiClient.createVariant(eq(BASIC), any(PipelineVariantDTO.class)))
                .thenReturn(variant("v3", "FIXED", 1024, 64));
        var result = handle("variant add FIXED 1024 64");
        assertThat(result).contains("Variant created on " + BASIC, "v3", "variant v3");
        ArgumentCaptor<PipelineVariantDTO> captor = ArgumentCaptor.captor();
        verify(apiClient).createVariant(eq(BASIC), captor.capture());
        assertThat(captor.getValue().strategy()).isEqualTo("FIXED");
        assertThat(captor.getValue().chunkSize()).isEqualTo(1024);
        assertThat(captor.getValue().chunkOverlap()).isEqualTo(64);
        assertThat(captor.getValue().embeddingModel()).isEqualTo("nomic-embed-text");
    }

    @Test
    void createsVariantFromPromptsUsingTheActiveWindowAsDefault() {
        when(apiClient.activeVariant(BASIC)).thenReturn(Optional.of(variant("v1", "RECURSIVE", 512, 128)));
        when(prompter.pick(eq("Chunking strategy"), anyList())).thenReturn(Optional.of("TOKEN"));
        when(prompter.prompt("Chunk size [512]: ")).thenReturn("");
        when(prompter.prompt("Chunk overlap [128]: ")).thenReturn("");
        when(apiClient.createVariant(eq(BASIC), any(PipelineVariantDTO.class)))
                .thenReturn(variant("v2", "TOKEN", 512, 128));
        assertThat(handle("variant add")).contains("Variant created on " + BASIC);
        ArgumentCaptor<PipelineVariantDTO> captor = ArgumentCaptor.captor();
        verify(apiClient).createVariant(eq(BASIC), captor.capture());
        assertThat(captor.getValue().strategy()).isEqualTo("TOKEN");
        assertThat(captor.getValue().chunkSize()).isEqualTo(512);
        assertThat(captor.getValue().chunkOverlap()).isEqualTo(128);
    }

    @Test
    void rejectsUnknownStrategyWithoutCreatingAVariant() {
        when(apiClient.activeVariant(BASIC)).thenReturn(Optional.of(variant("v1", "RECURSIVE", 512, 128)));
        assertThat(handle("variant add NOPE 512 128")).contains("Unknown strategy");
        verify(apiClient, never()).createVariant(anyString(), any(PipelineVariantDTO.class));
    }

    @Test
    void rejectsInvalidChunkWindowWithoutCreatingAVariant() {
        when(apiClient.activeVariant(BASIC)).thenReturn(Optional.of(variant("v1", "RECURSIVE", 512, 128)));
        assertThat(handle("variant add FIXED 512 512")).contains("Chunk window");
        verify(apiClient, never()).createVariant(anyString(), any(PipelineVariantDTO.class));
    }

    private static PipelineVariantDTO variant(String id, String strategy, int size, int overlap) {
        return new PipelineVariantDTO(id, BASIC, strategy, "nomic-embed-text", 768, size, overlap);
    }
}
