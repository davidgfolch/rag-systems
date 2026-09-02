package com.rag.tui.client;

import com.rag.contract.model.ConversationDTO;
import com.rag.contract.model.ChatMessageDTO;
import com.rag.tui.support.StubModuleServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link MemoryClient} against a real HTTP server serving
 * the rag-memory conversation contract, proving the JSON mapping end-to-end.
 */
class MemoryClientIntegrationTest {

    private StubModuleServer stub;
    private MemoryClient sut;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubModuleServer();
        sut = new MemoryClient(RestClient.builder().baseUrl(stub.baseUrl()).build());
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void listsConversationsFromStubServer() {
        List<ConversationDTO> conversations = sut.conversations();

        assertThat(conversations).isEmpty();
    }

    @Test
    void listsMessagesForConversation() {
        List<ChatMessageDTO> messages = sut.messages("c1");

        assertThat(messages).isEmpty();
    }
}