package com.rag.agentic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.agentic.agents.QueryPlanner;
import com.rag.agentic.agents.ReflectionAgent;
import com.rag.agentic.agents.ToolCallParser;
import com.rag.agentic.orchestration.AgentLoop;
import com.rag.agentic.orchestration.AgenticChatService;
import com.rag.agentic.orchestration.ToolRegistry;
import com.rag.agentic.services.AgenticRetrievalService;
import com.rag.agentic.services.WebCrawlerClient;
import com.rag.agentic.tools.AgentTool;
import com.rag.agentic.tools.DocumentSearchTool;
import com.rag.agentic.tools.SearchTool;
import com.rag.agentic.tools.WebSearchTool;
import com.rag.common.app.config.RagPipelineConfiguration;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.services.ChatModelPort;
import com.rag.common.generation.StreamingChatModelPort;
import com.rag.common.generation.config.WebSocketConfiguration;
import com.rag.common.generation.ws.ChatWebSocketHandler;
import com.rag.common.ingestion.AsyncIngestionService;
import com.rag.common.ingestion.IngestionService;
import com.rag.common.ingestion.web.IngestionController;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Wiring for the rag-agentic module. Imports the shared pipeline and WebSocket
 * configuration from {@code rag-common-app} and adds the agent components: a
 * {@link ToolRegistry} of search/web tools, a bounded {@link AgentLoop} that
 * plans sub-queries, calls tools and reflects on sufficiency, and an
 * {@link AgenticChatService} exposing one-shot and streaming answers.
 */
@Configuration
@Import({RagPipelineConfiguration.class, WebSocketConfiguration.class})
public class RagAgenticConfig {

    @Bean
    public WebCrawlerClient webCrawlerClient(
            RestClient.Builder restClientBuilder,
            @Value("${rag.webcrawler.url:http://localhost:8085}") String baseUrl) {
        return new WebCrawlerClient(restClientBuilder.clone().baseUrl(baseUrl).build());
    }

    @Bean
    public QueryPlanner queryPlanner() {
        return new QueryPlanner();
    }

    @Bean
    public ReflectionAgent reflectionAgent() {
        return new ReflectionAgent();
    }

    @Bean
    public ToolCallParser toolCallParser(ObjectMapper objectMapper) {
        return new ToolCallParser(objectMapper);
    }

    @Bean
    public SearchTool searchTool(VectorStorePort vectorStore,
                                 @Value("${rag.agent.tool-top-k:5}") int defaultTopK) {
        return new SearchTool(vectorStore, defaultTopK);
    }

    @Bean
    public DocumentSearchTool documentSearchTool(VectorStorePort vectorStore,
                                                 @Value("${rag.agent.tool-top-k:5}") int defaultTopK) {
        return new DocumentSearchTool(vectorStore, defaultTopK);
    }

    @Bean
    public WebSearchTool webSearchTool(WebCrawlerClient webCrawlerClient) {
        return new WebSearchTool(webCrawlerClient);
    }

    @Bean
    public ToolRegistry toolRegistry(List<AgentTool> agentTools, SearchTool searchTool) {
        return new ToolRegistry(agentTools, searchTool);
    }

    @Bean
    public AgentLoop agentLoop(ToolRegistry toolRegistry, QueryPlanner queryPlanner,
                               ReflectionAgent reflectionAgent, ToolCallParser toolCallParser,
                               ChatModelPort chatModel,
                               @Value("${rag.agent.max-steps:3}") int maxSteps) {
        return new AgentLoop(toolRegistry, queryPlanner, reflectionAgent, toolCallParser, chatModel, maxSteps);
    }

    @Bean
    public AgenticChatService agenticChatService(AgentLoop agentLoop,
                                                 StreamingChatModelPort streamingChatModel) {
        return new AgenticChatService(agentLoop, streamingChatModel);
    }

    @Bean
    public AgenticRetrievalService agenticRetrievalService(VectorStorePort vectorStore, AgentLoop agentLoop) {
        return new AgenticRetrievalService(vectorStore, agentLoop);
    }

    @Bean
    public IngestionController ingestionController(IngestionService ingestionService, WebCrawlerClient webCrawlerClient,
                                                   AsyncIngestionService asyncIngestionService,
                                                   AgenticRetrievalService retrievalService) {
        return new IngestionController(ingestionService, webCrawlerClient::fetch, asyncIngestionService, retrievalService);
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler(
            AgenticChatService agenticChatService, ObjectMapper objectMapper, Tracer tracer) {
        return new ChatWebSocketHandler(agenticChatService::askStream, objectMapper, tracer);
    }
}