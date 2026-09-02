package com.rag.tui.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.tui.client.ChatGateway;
import com.rag.tui.client.MemoryClient;
import com.rag.tui.client.ModuleHealthClient;
import com.rag.tui.client.RagApiClient;
import com.rag.tui.launcher.Module;
import com.rag.tui.launcher.ModuleLifecycleManager;
import com.rag.tui.launcher.ModuleRegistry;
import com.rag.common.services.FileDocumentLoader;
import com.rag.tui.ui.CommandDispatcher;
import com.rag.tui.ui.InteractiveShell;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Wiring for the thin rag-tui: module control plane + REST/WS clients to the
 * active rag-* module. No RAG logic (chunking/parsing/chat) lives in the TUI.
 */
@Configuration
public class RagTuiConfig {

    @Bean
    public ModuleRegistry moduleRegistry(
            @Value("${rag.tui.active:rag-basic}") String active,
            @Value("${RAG_BASIC_URL:http://localhost:8081}") String basicUrl,
            @Value("${RAG_ADVANCED_URL:http://localhost:8082}") String advancedUrl,
            @Value("${RAG_AGENTIC_URL:http://localhost:8083}") String agenticUrl) {
        List<Module> modules = List.of(
                new Module("rag-basic", basicUrl),
                new Module("rag-advanced", advancedUrl),
                new Module("rag-agentic", agenticUrl));
        return new ModuleRegistry(modules, active);
    }

    @Bean
    public ModuleLifecycleManager moduleLifecycleManager(
            @Value("${rag.tui.project-dir:}") String projectDir) {
        return new ModuleLifecycleManager(projectDir.isBlank() ? repoRoot() : projectDir);
    }

    private String repoRoot() {
        String script = System.getProperty("os.name", "").toLowerCase().contains("win") ? "run.bat" : "run.sh";
        Path dir = Path.of(System.getProperty("user.dir"));
        while (dir != null && !Files.isRegularFile(dir.resolve("scripts").resolve(script))) {
            dir = dir.getParent();
        }
        return dir == null ? System.getProperty("user.dir") : dir.toString();
    }

    @Bean
    public RagApiClient ragApiClient(ModuleRegistry registry) {
        return new RagApiClient(registry, RestClient.builder());
    }

    @Bean
    public MemoryClient memoryClient(@Value("${RAG_MEMORY_URL:http://localhost:8084}") String memoryUrl) {
        return new MemoryClient(RestClient.builder().baseUrl(memoryUrl).build());
    }

    @Bean
    public ModuleHealthClient moduleHealthClient() {
        return new ModuleHealthClient(RestClient.builder());
    }

    @Bean
    public ChatGateway chatGateway(ModuleRegistry registry, ObjectMapper objectMapper) {
        return new ChatGateway(registry, new StandardWebSocketClient(), objectMapper);
    }

    @Bean
    public FileDocumentLoader fileDocumentLoader() {
        return new FileDocumentLoader();
    }

    @Bean
    public CommandDispatcher commandDispatcher(ModuleRegistry registry, ModuleLifecycleManager lifecycle,
                                               RagApiClient apiClient, ChatGateway chatGateway,
                                               MemoryClient memoryClient, FileDocumentLoader fileLoader,
                                               ModuleHealthClient healthClient,
                                               @Value("${rag.tui.start-timeout-ms:120000}") long startTimeoutMs,
                                               @Value("${rag.chat.top-k:4}") int topK) {
        var clients = new CommandDispatcher.RagClients(apiClient, chatGateway, memoryClient, fileLoader, healthClient);
        var settings = new CommandDispatcher.Settings(startTimeoutMs, topK);
        return new CommandDispatcher(registry, lifecycle, clients, settings);
    }

    @Bean
    public ApplicationRunner tuiRunner(CommandDispatcher dispatcher) {
        return args -> new InteractiveShell(dispatcher,
                new InputStreamReader(System.in), new OutputStreamWriter(System.out)).run(); // NOSONAR java:S106 - interactive CLI output must go to the console
    }
}