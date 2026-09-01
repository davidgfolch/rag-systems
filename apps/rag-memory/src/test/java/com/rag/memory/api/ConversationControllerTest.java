package com.rag.memory.api;

import com.rag.contract.model.ChatMessageDTO;
import com.rag.contract.model.ConversationDTO;
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
        when(service.listConversations()).thenReturn(List.of(new ConversationDTO().id("c1")));

        List<ConversationDTO> result = sut.listConversations();

        assertThat(result).extracting(ConversationDTO::getId).containsExactly("c1");
    }

    @Test
    void createsConversationAndReturnsCreated() {
        when(service.createConversation("My chat")).thenReturn(new ConversationDTO().id("c1").title("My chat"));

        ResponseEntity<ConversationDTO> result = sut.createConversation("My chat");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().getId()).isEqualTo("c1");
        verify(service).createConversation("My chat");
    }

    @Test
    void listsMessagesForConversation() {
        when(service.listMessages("c1")).thenReturn(
                List.of(new ChatMessageDTO().id("m1").content("hi")));

        List<ChatMessageDTO> result = sut.listMessages("c1");

        assertThat(result).hasSize(1);
    }

    @Test
    void addsMessageAndReturnsCreated() {
        ChatMessageDTO input = new ChatMessageDTO().content("hi");
        when(service.addMessage("c1", input)).thenReturn(new ChatMessageDTO().id("m1").conversationId("c1"));

        ResponseEntity<ChatMessageDTO> result = sut.addMessage("c1", input);

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