package com.rag.tui.ui;

import com.rag.tui.client.ProviderClient;
import com.rag.tui.support.StubProviderServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the runtime provider-registration flow against a real HTTP stub:
 * the entered type/base URL/API key must reach the configure endpoint.
 */
class ConfigureProviderFlowIntegrationTest {

    private StubProviderServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubProviderServer();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void registersProviderWithEnteredDetails() {
        var prompter = new ScriptedPrompter("OPENAI_COMPATIBLE",
                "https://openrouter.ai/api/v1", "sk-or-1");
        var sut = new ConfigureProviderFlow(
                new ProviderClient(server.baseUrl(), RestClient.builder()), prompter);
        boolean configured = sut.configure("openrouter");
        assertThat(configured).isTrue();
        assertThat(server.lastConfigureBody())
                .contains("\"providerId\":\"openrouter\"")
                .contains("\"type\":\"OPENAI_COMPATIBLE\"")
                .contains("\"baseUrl\":\"https://openrouter.ai/api/v1\"")
                .contains("\"apiKey\":\"sk-or-1\"");
    }

    @Test
    void skipsApiKeyPromptForLocalOllama() {
        var prompter = new ScriptedPrompter("OLLAMA", "http://localhost:11434");
        var sut = new ConfigureProviderFlow(
                new ProviderClient(server.baseUrl(), RestClient.builder()), prompter);
        boolean configured = sut.configure("ollama-pc");
        assertThat(configured).isTrue();
        assertThat(server.lastConfigureBody())
                .contains("\"type\":\"OLLAMA\"")
                .contains("\"apiKey\":\"\"");
    }

    @Test
    void usesCatalogBaseUrlWithoutPrompting() {
        var prompter = new ScriptedPrompter("OPENAI_COMPATIBLE", "sk-ollama");
        var sut = new ConfigureProviderFlow(
                new ProviderClient(server.baseUrl(), RestClient.builder()), prompter);
        boolean configured = sut.configure("ollama");
        assertThat(configured).isTrue();
        assertThat(server.lastConfigureBody())
                .contains("\"baseUrl\":\"http://ollama.local\"")
                .contains("\"apiKey\":\"sk-ollama\"");
    }

    @Test
    void cancelsWithoutConfiguringWhenTypePickIsEmpty() {
        var prompter = new ScriptedPrompter(null);
        var sut = new ConfigureProviderFlow(
                new ProviderClient(server.baseUrl(), RestClient.builder()), prompter);
        assertThat(sut.configure("glhf")).isFalse();
        assertThat(server.lastConfigureBody()).isNull();
    }

    private static final class ScriptedPrompter implements Prompter {

        private final String pickValue;
        private final ArrayDeque<String> answers = new ArrayDeque<>();

        ScriptedPrompter(String pickValue, String... answers) {
            this.pickValue = pickValue;
            this.answers.addAll(List.of(answers));
        }

        @Override
        public Optional<String> pick(String title, List<Choice> choices) {
            return Optional.ofNullable(pickValue);
        }

        @Override
        public String prompt(String promptText) {
            String answer = answers.poll();
            return answer == null ? "" : answer;
        }
    }
}