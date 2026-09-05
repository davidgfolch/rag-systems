package com.rag.common.services;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Retrieval-augmented chat: retrieve relevant chunks, build a grounded prompt,
 * and generate an answer via the {@link ChatModel}. Supports both one-shot and
 * streaming answers (cancellable by disposing the returned {@link Flux}).
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

    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    public ChatService(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    public Flux<String> askStream(String question, int topK) {
        List<Chunk> sources = retrieve(question, topK);
        String prompt = PROMPT_TEMPLATE.formatted(context(sources), question);
        log.info("askStream: topK={}, sources={}, questionLength={}", topK, sources.size(), question.length());
        log.debug("askStream prompt length: {} chars", prompt.length());
        return chatModel.completeStream(prompt);
    }

    public ChatResult ask(String question, int topK) {
        List<Chunk> sources = retrieve(question, topK);
        String answer = chatModel.complete(PROMPT_TEMPLATE.formatted(context(sources), question));
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