package com.rag.common.app.pipeline;

import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.ingestion.chunking.RecursiveCharacterChunker;
import com.rag.common.ingestion.chunking.SemanticChunker;
import com.rag.common.ingestion.chunking.TokenChunker;
import com.rag.contract.pipeline.PipelineVariantDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VariantStoreTest {

    private static final String MODULE = "rag-basic";

    private final VectorStorePort bootStore = mock(VectorStorePort.class);
    private final VectorStorePort variantStore = mock(VectorStorePort.class);
    private final VariantStores storage = mock(VariantStores.class);
    private final VariantRepository repository = mock(VariantRepository.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private VariantStore sut;

    @BeforeEach
    void setUp() {
        when(storage.create(any())).thenReturn(variantStore);
        when(storage.embedding(any())).thenReturn(embeddingModel);
        when(repository.load()).thenReturn(List.of());
        sut = new VariantStore(MODULE, new RecursiveCharacterChunker(256, 32), storage, bootStore, repository);
    }

    private static PipelineVariantDTO variant(String strategy, int size, int overlap) {
        return new PipelineVariantDTO("ignored", null, strategy, "nomic-embed-text", 768, size, overlap);
    }

    private static VariantRepository.PersistedVariant persisted(String id, int size, int overlap,
                                                                boolean boot, boolean active) {
        return new VariantRepository.PersistedVariant(
                new PipelineVariantDTO(id, null, "RECURSIVE", "nomic-embed-text", 768, size, overlap), boot, active);
    }

    @Test
    void addGeneratesSequentialIdsAndForcesTheServedModule() {
        var first = sut.add(variant("RECURSIVE", 512, 128));
        var second = sut.add(variant("TOKEN", 256, 64));
        assertThat(first.variantId()).isEqualTo("v1");
        assertThat(second.variantId()).isEqualTo("v2");
        assertThat(sut.variants()).extracting(PipelineVariantDTO::module).containsOnly(MODULE);
    }

    @Test
    void firstRegisteredVariantBecomesActive() {
        sut.add(variant("RECURSIVE", 512, 128));
        assertThat(sut.active().orElseThrow().variantId()).isEqualTo("v1");
    }

    @Test
    void splitterFollowsTheActiveVariantStrategy() {
        sut.add(variant("TOKEN", 256, 64));
        assertThat(sut.splitter()).isInstanceOf(TokenChunker.class);
        var recursive = sut.add(variant("RECURSIVE", 512, 128));
        sut.activate(recursive.variantId());
        assertThat(sut.splitter()).isInstanceOf(RecursiveCharacterChunker.class);
        var semantic = sut.add(variant("SEMANTIC", 512, 128));
        sut.activate(semantic.variantId());
        assertThat(sut.splitter()).isInstanceOf(SemanticChunker.class);
    }

    @Test
    void storeBuildsOneStorePerActivatedVariant() {
        var variant = sut.add(variant("RECURSIVE", 512, 128));
        sut.store();
        sut.store();
        verify(storage, times(1)).create(any());
        assertThat(sut.store()).isSameAs(variantStore);
        assertThat(sut.find(variant.variantId())).isPresent();
    }

    @Test
    void embeddingFollowsTheActiveVariantAndIsCached() {
        var token = sut.add(variant("TOKEN", 256, 64));
        assertThat(sut.embeddingModel()).isSameAs(embeddingModel);
        assertThat(sut.embeddingModel()).isSameAs(embeddingModel);
        verify(storage, times(1)).embedding(token);
    }

    @Test
    void seededBootVariantKeepsTheModuleStore() {
        sut.seed("boot", variant("RECURSIVE", 512, 128));
        assertThat(sut.store()).isSameAs(bootStore);
        verify(storage, never()).create(any());
    }

    @Test
    void removingTheActiveVariantFallsBackToBootStorage() {
        sut.seed("boot", variant("RECURSIVE", 512, 128));
        var variant = sut.add(variant("FIXED", 1024, 64));
        sut.activate(variant.variantId());
        assertThat(sut.store()).isSameAs(variantStore);
        sut.remove(variant.variantId());
        assertThat(sut.store()).isSameAs(bootStore);
    }

    @Test
    void removingAVariantDropsItsTableButNeverTheBootStore() {
        sut.seed("boot", variant("RECURSIVE", 512, 128));
        var variant = sut.add(variant("TOKEN", 256, 64));
        assertThat(sut.remove(variant.variantId())).isTrue();
        verify(storage).drop(variant);
        assertThat(sut.remove("boot")).isTrue();
        verify(storage, never()).drop(argThat(boot -> boot != null && "boot".equals(boot.variantId())));
    }

    @Test
    void rejectsUnknownStrategiesAndVariants() {
        var unknownStrategy = variant("nope", 512, 128);
        assertThatThrownBy(() -> sut.add(unknownStrategy))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sut.activate("missing"))
                .isInstanceOf(IllegalArgumentException.class);
        var validVariant = variant("RECURSIVE", 512, 128);
        assertThatThrownBy(() -> sut.add("bad id", validVariant))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(sut.remove("missing")).isFalse();
    }

    @Test
    void rejectsChunkWindowsThatCannotAdvance() {
        var fullOverlap = variant("FIXED", 512, 512);
        assertThatThrownBy(() -> sut.add(fullOverlap))
                .isInstanceOf(IllegalArgumentException.class);
        var overlapAboveWindow = variant("TOKEN", 7, 6);
        assertThatThrownBy(() -> sut.add(overlapAboveWindow))
                .isInstanceOf(IllegalArgumentException.class);
        var zeroWindow = variant("RECURSIVE", 0, 0);
        assertThatThrownBy(() -> sut.add(zeroWindow))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreReplacesTheSeededBootVariantAndKeepsThePersistedActiveOne() {
        sut.seed("boot", variant("RECURSIVE", 512, 128));
        when(repository.load()).thenReturn(List.of(persisted("boot", 256, 32, true, false),
                persisted("v4", 128, 16, false, true)));
        sut.restore();
        assertThat(sut.find("boot").orElseThrow().chunkSize()).isEqualTo(256);
        assertThat(sut.active().orElseThrow().variantId()).isEqualTo("v4");
        assertThat(sut.add(variant("FIXED", 64, 8)).variantId()).isEqualTo("v5");
    }

    @Test
    void restorePersistsTheSeededBootVariantOnAFirstRun() {
        sut.seed("boot", variant("RECURSIVE", 512, 128));
        sut.restore();
        assertThat(sut.variants()).extracting(PipelineVariantDTO::variantId).containsExactly("boot");
        verify(repository, atLeastOnce()).replaceAll(any(), eq("boot"), eq("boot"));
    }
}
