package com.rag.advanced;

import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the rag-advanced application. The PgVector auto-configuration
 * is excluded because {@code RagAdvancedConfig} builds the store with a schema
 * pinned to {@code rag_advanced}. Chat and embedding models are consumed
 * remotely from the rag-provider service, so no provider auto-config is present.
 */
@SpringBootApplication(exclude = {
        PgVectorStoreAutoConfiguration.class
})
public class RagAdvancedApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAdvancedApplication.class, args);
    }
}