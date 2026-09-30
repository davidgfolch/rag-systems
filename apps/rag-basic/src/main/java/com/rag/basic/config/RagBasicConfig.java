package com.rag.basic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.basic.services.RetrievalService;
import com.rag.basic.services.WebCrawlerClient;
import com.rag.common.app.config.RagPipelineConfiguration;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.generation.ChatService;
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

/**
 * Wiring for the rag-basic module. Imports the shared pipeline and WebSocket
 * configuration from {@code rag-common-app} and keeps only the beans that
 * distinguish rag-basic: its retrieval service, page fetcher and seams.
 */
@Configuration
@Import({RagPipelineConfiguration.class, WebSocketConfiguration.class})
public class RagBasicConfig {

    @Bean
    public WebCrawlerClient webCrawlerClient(
            RestClient.Builder restClientBuilder,
            @Value("${rag.webcrawler.url:http://localhost:8085}") String baseUrl) {
        return new WebCrawlerClient(restClientBuilder.clone().baseUrl(baseUrl).build());
    }

    @Bean
    public RetrievalService retrievalService(VectorStorePort vectorStore) {
        return new RetrievalService(vectorStore);
    }

    @Bean
    public IngestionController ingestionController(IngestionService ingestionService, WebCrawlerClient webCrawlerClient,
                                                   AsyncIngestionService asyncIngestionService, RetrievalService retrievalService) {
        return new IngestionController(ingestionService, webCrawlerClient::fetch, asyncIngestionService, retrievalService);
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler(
            ChatService chatService, ObjectMapper objectMapper, Tracer tracer) {
        return new ChatWebSocketHandler(chatService::askStream, objectMapper, tracer);
    }
}