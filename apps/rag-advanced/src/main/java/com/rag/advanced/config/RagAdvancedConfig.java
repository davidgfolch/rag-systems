package com.rag.advanced.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.advanced.retrieval.LexicalScorer;
import com.rag.advanced.services.AdvancedRetrievalService;
import com.rag.advanced.services.WebCrawlerClient;
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
 * Wiring for the rag-advanced module. Imports the shared pipeline and WebSocket
 * configuration from {@code rag-common-app} and adds an advanced retrieval
 * service that widens the vector pass and re-ranks candidates with a BM25-style
 * lexical pass. The pgvector schema is pinned to {@code rag_advanced} in
 * application.yml.
 */
@Configuration
@Import({RagPipelineConfiguration.class, WebSocketConfiguration.class})
public class RagAdvancedConfig {

    @Bean
    public WebCrawlerClient webCrawlerClient(
            RestClient.Builder restClientBuilder,
            @Value("${rag.webcrawler.url:http://localhost:8085}") String baseUrl) {
        return new WebCrawlerClient(restClientBuilder.clone().baseUrl(baseUrl).build());
    }

    @Bean
    public LexicalScorer lexicalScorer() {
        return new LexicalScorer();
    }

    @Bean
    public AdvancedRetrievalService advancedRetrievalService(
            VectorStorePort vectorStore, LexicalScorer lexicalScorer) {
        return new AdvancedRetrievalService(vectorStore, lexicalScorer);
    }

    @Bean
    public IngestionController ingestionController(IngestionService ingestionService, WebCrawlerClient webCrawlerClient,
                                                   AsyncIngestionService asyncIngestionService,
                                                   AdvancedRetrievalService retrievalService) {
        return new IngestionController(ingestionService, webCrawlerClient::fetch, asyncIngestionService, retrievalService);
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler(
            ChatService chatService, ObjectMapper objectMapper, Tracer tracer) {
        return new ChatWebSocketHandler(chatService::askStream, objectMapper, tracer);
    }
}