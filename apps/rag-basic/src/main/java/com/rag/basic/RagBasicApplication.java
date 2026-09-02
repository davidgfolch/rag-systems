package com.rag.basic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;

/**
 * Entry point for the rag-basic application. The provider auto-configurations
 * (Postgres vector store, Ollama and OpenAI models) are excluded because
 * {@code RagBasicConfig} defines one bean per active profile explicitly,
 * avoiding ambiguous beans and key validation failures when both providers
 * (Ollama for {@code local}, OpenAI for {@code cloud}) share the classpath.
 */
@SpringBootApplication(exclude = {
        PgVectorStoreAutoConfiguration.class,
        OllamaEmbeddingAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class,
        OllamaChatAutoConfiguration.class,
        OpenAiChatAutoConfiguration.class,
        OpenAiAudioSpeechAutoConfiguration.class,
        OpenAiAudioTranscriptionAutoConfiguration.class,
        OpenAiImageAutoConfiguration.class,
        OpenAiModerationAutoConfiguration.class
})
public class RagBasicApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagBasicApplication.class, args);
    }
}