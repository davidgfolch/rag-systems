package com.rag.common.services;

import com.rag.common.domain.Chunk;
import com.rag.common.repositories.VectorStorePort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private final VectorStorePort vectorStore = mock(VectorStorePort.class);
    private final ChatModelPort chatModel = mock(ChatModelPort.class);
    private final ChatService sut = new ChatService(vectorStore, chatModel);

    @Test
    void answersQuestionFromRetrievedContext() {
        var source = new Chunk("c1", "d1", "Spring AI simplifies RAG.", 0, Map.of());
        when(vectorStore.similaritySearch("what is spring ai", 3)).thenReturn(List.of(source));
        when(chatModel.complete(anyString())).thenReturn("Spring AI is a framework.");
        var result = sut.ask("what is spring ai", 3);
        assertThat(result.answer()).isEqualTo("Spring AI is a framework.");
        assertThat(result.sources()).containsExactly(source);
        verify(chatModel).complete(anyString());
    }

    @Test
    void buildsPromptIncludingRetrievedChunks() {
        var source = new Chunk("c1", "d1", "Retrieval fetches relevant chunks.", 0, Map.of());
        when(vectorStore.similaritySearch("how does retrieval work", 2)).thenReturn(List.of(source));
        when(chatModel.complete(anyString())).thenReturn("It fetches chunks.");
        sut.ask("how does retrieval work", 2);
        verify(chatModel).complete(contains("Retrieval fetches relevant chunks."));
    }

    @Test
    void streamsTokensFromChatModel() {
        var source = new Chunk("c1", "d1", "Context chunk.", 0, Map.of());
        when(vectorStore.similaritySearch("q", 2)).thenReturn(List.of(source));
        when(chatModel.completeStream(anyString()))
                .thenReturn(Flux.just("Spring", " AI", " works."));
        var stream = sut.askStream("q", 2);
        StepVerifier.create(stream)
                .expectNext("Spring", " AI", " works.")
                .verifyComplete();
        verify(chatModel).completeStream(anyString());
    }

    @Test
    void handlesNoContextRetrieved() {
        when(vectorStore.similaritySearch("q", 2)).thenReturn(List.of());
        when(chatModel.complete(anyString())).thenReturn("I don't know.");
        var result = sut.ask("q", 2);
        assertThat(result.answer()).isEqualTo("I don't know.");
        assertThat(result.sources()).isEmpty();
    }
}