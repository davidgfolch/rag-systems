package com.rag.tui.services;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStore;
import com.rag.common.services.ChatModel;

import java.util.List;

/**
 * Implements retrieval-augmented question answering: retrieve relevant chunks,
 * build a grounded prompt, and generate an answer via the {@link ChatModel}.
 */
public class ChatService {

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

    public ChatResult ask(String question, int topK) {
        List<Chunk> sources = vectorStore.similaritySearch(question, topK);
        String context = sources.stream()
                .map(Chunk::getContent)
                .reduce((a, b) -> a + "\n---\n" + b)
                .orElse("(no context retrieved)");
        String answer = chatModel.complete(PROMPT_TEMPLATE.formatted(context, question));
        return new ChatResult(answer, sources);
    }

    public record ChatResult(String answer, List<Chunk> sources) {}
}
