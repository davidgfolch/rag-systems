package com.rag.memory.api;

import com.rag.contract.model.ChatMessage;
import com.rag.contract.model.Conversation;
import com.rag.memory.services.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationControllerTest {

    private final ConversationService service = mock(ConversationService.class);
    private final ConversationController sut = new ConversationController(service);

    @Test
    void listsConversations() {
        when(service.listConversations()).thenReturn(List.of(new Conversation().id("c1")));

        List<Conversation> result = sut.listConversations();

        assertThat(result).extracting(Conversation::getId).containsExactly("c1");
    }

    @Test
    void createsConversationAndReturnsCreated() {
        when(service.createConversation("My chat")).thenReturn(new Conversation().id("c1").title("My chat"));

        ResponseEntity<Conversation> result = sut.createConversation("My chat");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getId()).isEqualTo("c1");
        verify(service).createConversation("My chat");
    }

    @Test
    void listsMessagesForConversation() {
        when(service.listMessages("c1")).thenReturn(
                List.of(new ChatMessage().id("m1").content("hi")));

        List<ChatMessage> result = sut.listMessages("c1");

        assertThat(result).hasSize(1);
    }

    @Test
    void addsMessageAndReturnsCreated() {
        ChatMessage input = new ChatMessage().content("hi");
        when(service.addMessage("c1", input)).thenReturn(new ChatMessage().id("m1").conversationId("c1"));

        ResponseEntity<ChatMessage> result = sut.addMessage("c1", input);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getConversationId()).isEqualTo("c1");
    }

    @Test
    void mapsIllegalArgumentToNotFound() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<String> result = handler.handleIllegalArgument(new IllegalArgumentException("Unknown conversation"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isEqualTo("Unknown conversation");
    }
}