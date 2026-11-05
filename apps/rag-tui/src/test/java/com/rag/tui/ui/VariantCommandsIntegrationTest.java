package com.rag.tui.ui;

import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.tui.support.StubModuleServer;
import com.rag.tui.testfixture.TestModules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives {@link VariantCommands} against a real {@link RagApiClient} hitting the
 * stub module server: variant listing and activation run over HTTP, only the
 * module backing is stubbed.
 */
class VariantCommandsIntegrationTest {

    private static final String VARIANTS = "[{\"variantId\":\"v1\",\"module\":\"rag-basic\",\"strategy\":\"RECURSIVE\","
            + "\"embeddingModel\":\"nomic-embed-text\",\"embeddingDimension\":768,\"chunkSize\":512,\"chunkOverlap\":128},"
            + "{\"variantId\":\"v2\",\"module\":\"rag-basic\",\"strategy\":\"TOKEN\","
            + "\"embeddingModel\":\"nomic-embed-text\",\"embeddingDimension\":768,\"chunkSize\":256,\"chunkOverlap\":64}]";
    private static final String RECURSIVE = "{\"variantId\":\"v1\",\"module\":\"rag-basic\",\"strategy\":\"RECURSIVE\","
            + "\"embeddingModel\":\"nomic-embed-text\",\"embeddingDimension\":768,\"chunkSize\":512,\"chunkOverlap\":128}";
    private static final String TOKEN = "{\"variantId\":\"v2\",\"module\":\"rag-basic\",\"strategy\":\"TOKEN\","
            + "\"embeddingModel\":\"nomic-embed-text\",\"embeddingDimension\":768,\"chunkSize\":256,\"chunkOverlap\":64}";

    private StubModuleServer stub;
    private Prompter prompter;
    private VariantCommands sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        stub.variants(VARIANTS);
        prompter = mock(Prompter.class);
        var registry = new ModuleRegistry(List.of(TestModules.withUrl(stub.baseUrl())), TestModules.BASIC);
        sut = new VariantCommands(registry, new RagApiClient(registry, RestClient.builder()), prompter);
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void listsVariantsOverHttpAndActivatesThePickedOne() {
        stub.activationResponse(TOKEN);
        when(prompter.pick(eq("Switch pipeline variant"), anyList())).thenReturn(Optional.of("v2"));
        var result = sut.switchVariant("");
        assertThat(result).contains("TOKEN", "256/64", "768d");
        assertThat(stub.lastActivatedVariant()).isEqualTo("v2");
    }

    @Test
    void activatesTheVariantNamedOnTheCommandLine() {
        stub.activationResponse(RECURSIVE);
        assertThat(sut.switchVariant("v1")).contains("RECURSIVE", "512/128");
        assertThat(stub.lastActivatedVariant()).isEqualTo("v1");
    }

    @Test
    void createsAVariantOverHttpInheritingTheActiveEmbedding() {
        stub.activeVariant(RECURSIVE);
        stub.createdVariant(TOKEN);
        assertThat(sut.switchVariant("add TOKEN 256 64")).contains("Variant created on " + TestModules.BASIC, "v2");
        assertThat(stub.lastCreatedVariantBody())
                .contains("\"strategy\":\"TOKEN\"", "\"chunkSize\":256", "\"chunkOverlap\":64")
                .contains("\"embeddingModel\":\"nomic-embed-text\"", "\"embeddingDimension\":768");
    }

    @Test
    void reportsUnknownVariantWithoutActivating() {
        assertThat(sut.switchVariant("v9")).contains("Unknown variant: v9");
        assertThat(stub.lastActivatedVariant()).isNull();
    }
}
