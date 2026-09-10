package com.rag.tui.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static com.rag.contract.constants.ApiPaths.CATALOG;
import static com.rag.contract.constants.ApiPaths.CATALOG_REFRESH;
import static com.rag.contract.constants.ApiPaths.PROVIDER;
import static com.rag.contract.constants.ApiPaths.PROVIDER_CHAT;
import static com.rag.contract.constants.ApiPaths.PROVIDER_EMBEDDING;
import static com.rag.tui.testfixture.TestModelCatalogs.LOCAL_CHAT;
import static com.rag.tui.testfixture.TestModelCatalogs.LOCAL_EMBED;
import static com.rag.tui.testfixture.TestModelCatalogs.PROVIDER_OLLAMA;
import static com.rag.tui.testfixture.TestModelCatalogs.SOURCE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderClientTest {

    private static final String BASE = "http://provider";

    private final RestClient.Builder builder = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory());
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ProviderClient sut = new ProviderClient(BASE, builder);

    private static final String STATUS_JSON = """
            {"chat":{"providerId":"ollama","model":"phi4"},
             "embedding":{"providerId":"ollama","model":"nomic-embed-text"},
             "provider":"ollama","embeddingDimension":768}""";

    private static final String CATALOG_JSON = """
            {"models":[
              {"providerId":"ollama","modelId":"phi4","name":"Phi-4",
               "limits":{"context":16384,"output":4096},
               "capabilities":{"reasoning":true,"toolCall":false,"structuredOutput":false},
               "cost":null,"status":null}],
             "source":"https://models.dev/api.json",
             "fetchedAt":"2026-09-08T10:00:00Z"}""";

    @Test
    void returnsProviderStatus() {
        server.expect(requestTo(BASE + PROVIDER))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(STATUS_JSON, MediaType.APPLICATION_JSON));

        var status = sut.status();

        assertThat(status.chat().providerId()).isEqualTo(PROVIDER_OLLAMA);
        assertThat(status.chat().model()).isEqualTo(LOCAL_CHAT);
        assertThat(status.embedding().model()).isEqualTo(LOCAL_EMBED);
        assertThat(status.embeddingDimension()).isEqualTo(768);
        server.verify();
    }

    @Test
    void returnsCatalog() {
        server.expect(requestTo(BASE + CATALOG))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(CATALOG_JSON, MediaType.APPLICATION_JSON));

        var catalog = sut.catalog();

        assertThat(catalog.models()).hasSize(1);
        assertThat(catalog.source()).isEqualTo(SOURCE);
        assertThat(catalog.fetchedAt()).isNotNull();
        assertThat(catalog.models().getFirst().modelId()).isEqualTo(LOCAL_CHAT);
        assertThat(catalog.models().getFirst().limits().context()).isEqualTo(16384);
        assertThat(catalog.models().getFirst().capabilities().reasoning()).isTrue();
        server.verify();
    }

    @Test
    void refreshesCatalog() {
        server.expect(requestTo(BASE + CATALOG_REFRESH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(CATALOG_JSON, MediaType.APPLICATION_JSON));

        var catalog = sut.refreshCatalog();

        assertThat(catalog.models()).hasSize(1);
        server.verify();
    }

    @Test
    void switchesChatModel() {
        server.expect(requestTo(BASE + PROVIDER_CHAT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"providerId\":\"ollama\",\"model\":\"phi4\"}"))
                .andRespond(withNoContent());

        sut.switchChat(PROVIDER_OLLAMA, LOCAL_CHAT);

        server.verify();
    }

    @Test
    void switchesEmbeddingModel() {
        server.expect(requestTo(BASE + PROVIDER_EMBEDDING))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"providerId\":\"openai\",\"model\":\"text-embedding-3-small\"}"))
                .andRespond(withNoContent());

        sut.switchEmbedding("openai", "text-embedding-3-small");

        server.verify();
    }
}