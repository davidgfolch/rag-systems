package com.rag.tui.services;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStore;
import com.rag.common.services.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final ChatService sut = new ChatService(vectorStore, chatModel);

    @Test
    void answersQuestionFromRetrievedContext() {
        Chunk source = new Chunk("c1", "d1", "Spring AI simplifies RAG.", 0, Map.of());
        when(vectorStore.similaritySearch("what is spring ai", 3)).thenReturn(List.of(source));
        when(chatModel.complete(anyString())).thenReturn("Spring AI is a framework.");

        ChatService.ChatResult result = sut.ask("what is spring ai", 3);

        assertThat(result.answer()).isEqualTo("Spring AI is a framework.");
        assertThat(result.sources()).containsExactly(source);
        verify(chatModel).complete(anyString());
    }

    @Test
    void buildsPromptIncludingRetrievedChunks() {
        Chunk source = new Chunk("c1", "d1", "Retrieval fetches relevant chunks.", 0, Map.of());
        when(vectorStore.similaritySearch("how does retrieval work", 2)).thenReturn(List.of(source));
        when(chatModel.complete(anyString())).thenReturn("It fetches chunks.");

        sut.ask("how does retrieval work", 2);

        verify(chatModel).complete(org.mockito.ArgumentMatchers.contains("Retrieval fetches relevant chunks."));
    }
}
