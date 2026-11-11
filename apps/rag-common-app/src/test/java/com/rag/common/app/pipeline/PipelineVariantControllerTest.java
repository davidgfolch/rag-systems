package com.rag.common.app.pipeline;

import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.ingestion.chunking.FixedSizeChunker;
import com.rag.contract.pipeline.ChunkingStrategy;
import com.rag.contract.pipeline.PipelineVariantDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PipelineVariantControllerTest {

    private static final String BASIC = "rag-basic";
    private static final String ADVANCED = "rag-advanced";

    private final VariantStore store = new VariantStore(BASIC, new FixedSizeChunker(256, 32),
            mock(VariantStores.class), mock(VectorStorePort.class));
    private final PipelineVariantController sut = new PipelineVariantController(store);

    private static PipelineVariantDTO request(String strategy, int size, int overlap) {
        return new PipelineVariantDTO(null, null, strategy, "nomic-embed-text", 768, size, overlap);
    }

    @Test
    void addRegistersVariantUnderGeneratedId() {
        var res = sut.add(BASIC, request(ChunkingStrategy.RECURSIVE.name(), 512, 128));
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().variantId()).isEqualTo("v1");
        assertThat(res.getBody().module()).isEqualTo(BASIC);
        assertThat(sut.list(BASIC).getBody()).hasSize(1);
    }

    @Test
    void addRejectsUnknownStrategy() {
        var res = sut.add(BASIC, request("unknown", 512, 128));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(store.variants()).isEmpty();
    }

    @Test
    void addRejectsAnInvalidChunkWindow() {
        var res = sut.add(BASIC, request("FIXED", 512, 512));
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(store.variants()).isEmpty();
    }

    @Test
    void rejectsAModuleThisProcessDoesNotServe() {
        assertThat(sut.list(ADVANCED).getStatusCode().value()).isEqualTo(404);
        assertThat(sut.add(ADVANCED, request("RECURSIVE", 512, 128)).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void activateSwitchesTheVariantTheStoreServes() {
        sut.add(BASIC, request("RECURSIVE", 512, 128));
        var second = sut.add(BASIC, request("TOKEN", 256, 64)).getBody();
        assertThat(second).isNotNull();
        var res = sut.activate(BASIC, second.variantId());
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(sut.active(BASIC).getBody()).isNotNull();
        assertThat(sut.active(BASIC).getBody().variantId()).isEqualTo(second.variantId());
        assertThat(sut.activate(BASIC, "nope").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void updateReplacesTheChunkingWindow() {
        sut.add(BASIC, request("RECURSIVE", 512, 128));
        var updated = sut.update(BASIC, "v1", request("FIXED", 1024, 64));
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().strategy()).isEqualTo("FIXED");
        assertThat(updated.getBody().chunkSize()).isEqualTo(1024);
        assertThat(sut.update(BASIC, "missing", request("FIXED", 1024, 64)).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deleteRemovesTheVariantAndClearsTheActiveOne() {
        sut.add(BASIC, request("RECURSIVE", 512, 128));
        assertThat(sut.delete(BASIC, "v1").getStatusCode().value()).isEqualTo(204);
        assertThat(sut.active(BASIC).getStatusCode().value()).isEqualTo(204);
        assertThat(sut.delete(BASIC, "v1").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void activeReportsNoContentWhenNothingIsRegistered() {
        assertThat(sut.active(BASIC).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void getReturnsTheVariantOnlyForItsModule() {
        sut.add(BASIC, request("RECURSIVE", 512, 128));
        assertThat(sut.get(BASIC, "v1").getStatusCode().value()).isEqualTo(200);
        assertThat(sut.get(BASIC, "missing").getStatusCode().value()).isEqualTo(404);
    }
}
