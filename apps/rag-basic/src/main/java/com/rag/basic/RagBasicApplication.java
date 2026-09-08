package com.rag.basic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;

/**
 * Entry point for the rag-basic application. The PgVector auto-configuration is
 * excluded because {@code RagBasicConfig} builds the store with a schema derived
 * from the active embedding dimension. Chat and embedding models are consumed
 * remotely from the rag-provider service, so no provider auto-config is present.
 */
@SpringBootApplication(exclude = {
        PgVectorStoreAutoConfiguration.class
})
public class RagBasicApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagBasicApplication.class, args);
    }
}