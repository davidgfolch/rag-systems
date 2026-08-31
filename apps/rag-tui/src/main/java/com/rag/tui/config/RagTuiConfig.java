package com.rag.tui.config;

import com.rag.common.repositories.VectorStore;
import com.rag.common.services.ChatModel;
import com.rag.common.services.DocumentParser;
import com.rag.common.services.EmbeddingModel;
import com.rag.common.services.TextSplitter;
import com.rag.tui.adapter.SpringAiChatModel;
import com.rag.tui.adapter.SpringAiEmbeddingModel;
import com.rag.tui.chunking.RecursiveCharacterChunker;
import com.rag.tui.fetching.JsoupWebPageFetcher;
import com.rag.tui.fetching.WebPageFetcher;
import com.rag.tui.parsing.PlainTextParser;
import com.rag.tui.parsing.TikaDocumentParser;
import com.rag.tui.services.ChatService;
import com.rag.tui.services.FileDocumentLoader;
import com.rag.tui.services.IngestionService;
import com.rag.tui.ui.CommandDispatcher;
import com.rag.tui.ui.InteractiveShell;
import com.rag.tui.vectorstore.InMemoryVectorStore;
import com.rag.tui.vectorstore.PgVectorStoreAdapter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Wiring for the rag-tui module. Exposes the domain strategy interfaces and
 * picks concrete implementations from application properties (SoC, DIP).
 */
@Configuration
public class RagTuiConfig {

    @Bean
    public TextSplitter textSplitter(
            @Value("${rag.chunking.size:512}") int size,
            @Value("${rag.chunking.overlap:64}") int overlap) {
        return new RecursiveCharacterChunker(size, overlap);
    }

    @Bean
    public DocumentParser documentParser(@Value("${rag.parsing.mode:plain}") String mode) {
        return "tika".equalsIgnoreCase(mode) ? new TikaDocumentParser() : new PlainTextParser();
    }

    @Bean
    public EmbeddingModel embeddingModel(org.springframework.ai.embedding.EmbeddingModel delegate) {
        return new SpringAiEmbeddingModel(delegate);
    }

    @Bean
    public ChatModel chatModel(ChatClient.Builder builder) {
        return new SpringAiChatModel(builder.build());
    }

    @Bean
    public VectorStore vectorStore(
            @Value("${rag.vector-store.type:in-memory}") String type,
            EmbeddingModel embeddingModel,
            ObjectProvider<org.springframework.ai.vectorstore.VectorStore> springAiStore) {
        if ("pgvector".equalsIgnoreCase(type) && springAiStore.getIfAvailable() != null) {
            return new PgVectorStoreAdapter(springAiStore.getObject());
        }
        return new InMemoryVectorStore(embeddingModel);
    }

    @Bean
    public IngestionService ingestionService(DocumentParser parser, TextSplitter splitter,
                                             EmbeddingModel embeddingModel, VectorStore vectorStore) {
        return new IngestionService(parser, splitter, embeddingModel, vectorStore);
    }

    @Bean
    public ChatService chatService(VectorStore vectorStore, ChatModel chatModel) {
        return new ChatService(vectorStore, chatModel);
    }

    @Bean
    public WebPageFetcher webPageFetcher() {
        return new JsoupWebPageFetcher();
    }

    @Bean
    public FileDocumentLoader fileDocumentLoader() {
        return new FileDocumentLoader();
    }

    @Bean
    public CommandDispatcher commandDispatcher(IngestionService ingestionService, ChatService chatService,
                                               WebPageFetcher webPageFetcher, FileDocumentLoader fileLoader,
                                               @Value("${rag.chat.top-k:4}") int topK) {
        return new CommandDispatcher(ingestionService, chatService, webPageFetcher, fileLoader, topK);
    }

    @Bean
    public ApplicationRunner tuiRunner(CommandDispatcher dispatcher) {
        return args -> new InteractiveShell(dispatcher,
                new InputStreamReader(System.in), new OutputStreamWriter(System.out)).run();
    }
}
