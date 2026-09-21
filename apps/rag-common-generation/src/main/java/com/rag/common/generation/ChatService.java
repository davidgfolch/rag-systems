package com.rag.common.generation;

import com.rag.common.core.domain.Chunk;
import com.rag.common.core.repositories.VectorStorePort;
import com.rag.common.core.services.ChatModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Retrieval-augmented chat: retrieve relevant chunks, build a grounded prompt,
 * and generate an answer via the {@link ChatModelPort} (one-shot) or the
 * {@link StreamingChatModelPort} (streaming, cancellable by disposing the
 * returned {@link Flux}).
 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String PROMPT_TEMPLATE = """
            You are a helpful assistant. Answer the question using ONLY the provided
            context. If the answer is not in the context, say you do not know.

            Context:
            %s

            Question: %s
            Answer:""";

    private final VectorStorePort vectorStore;
    private final ChatModelPort chatModel;
    private final StreamingChatModelPort streamingChatModel;

    public ChatService(VectorStorePort vectorStore, ChatModelPort chatModel, StreamingChatModelPort streamingChatModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    public Flux<String> askStream(String question, int topK) {
        var sources = retrieve(question, topK);
        var prompt = PROMPT_TEMPLATE.formatted(context(sources), question);
        log.info("askStream: topK={}, sources={}, questionLength={}", topK, sources.size(), question.length());
        log.debug("askStream prompt length: {} chars", prompt.length());
        return streamingChatModel.completeStream(prompt);
    }

    public ChatResult ask(String question, int topK) {
        var sources = retrieve(question, topK);
        var answer = chatModel.complete(PROMPT_TEMPLATE.formatted(context(sources), question));
        log.info("ask: topK={}, sources={}, answerLength={}", topK, sources.size(), answer.length());
        return new ChatResult(answer, sources);
    }

    private List<Chunk> retrieve(String question, int topK) {
        log.info("Retrieving up to {} chunks for ask", topK);
        return vectorStore.similaritySearch(question, topK);
    }

    private String context(List<Chunk> sources) {
        return sources.stream()
                .map(Chunk::getContent)
                .reduce((a, b) -> a + "\n---\n" + b)
                .orElse("(no context retrieved)");
    }

    public record ChatResult(String answer, List<Chunk> sources) {}
}