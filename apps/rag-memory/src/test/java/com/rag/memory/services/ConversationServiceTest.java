package com.rag.memory.services;

import com.rag.contract.model.ChatMessage;
import com.rag.memory.domain.Conversation;
import com.rag.memory.repositories.ConversationRepository;
import com.rag.memory.repositories.MessageRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final ConversationService sut =
            new ConversationService(conversationRepository, messageRepository);

    @Test
    void createsConversationWithDefaultTitleWhenBlank() {
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        com.rag.contract.model.Conversation result = sut.createConversation("   ");

        assertThat(result.getTitle()).isEqualTo("New conversation");
        assertThat(result.getId()).isNotBlank();
    }

    @Test
    void createsConversationWithTrimmedTitle() {
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        com.rag.contract.model.Conversation result = sut.createConversation("  My chat  ");

        assertThat(result.getTitle()).isEqualTo("My chat");
    }

    @Test
    void listsConversationsNewestFirst() {
        Conversation older = new Conversation("c1", "old", OffsetDateTime.now().minusDays(1));
        Conversation newer = new Conversation("c2", "new", OffsetDateTime.now());
        when(conversationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(newer, older));

        List<com.rag.contract.model.Conversation> result = sut.listConversations();

        assertThat(result).extracting(com.rag.contract.model.Conversation::getId)
                .containsExactly("c2", "c1");
    }

    @Test
    void listsMessagesForConversation() {
        com.rag.memory.domain.ChatMessage stored = new com.rag.memory.domain.ChatMessage(
                "m1", "c1", "user", "hello", OffsetDateTime.parse("2026-01-01T10:00:00+01:00"));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(stored));

        List<ChatMessage> result = sut.listMessages("c1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("m1");
        assertThat(result.get(0).getRole()).isEqualTo(ChatMessage.RoleEnum.USER);
    }

    @Test
    void addsMessageToExistingConversation() {
        when(conversationRepository.existsById("c1")).thenReturn(true);
        when(messageRepository.save(any(com.rag.memory.domain.ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessage input = new ChatMessage().role(ChatMessage.RoleEnum.USER).content("hello");
        ChatMessage result = sut.addMessage("c1", input);

        assertThat(result.getConversationId()).isEqualTo("c1");
        assertThat(result.getRole()).isEqualTo(ChatMessage.RoleEnum.USER);
        verify(messageRepository).save(any(com.rag.memory.domain.ChatMessage.class));
    }

    @Test
    void rejectsMessageToUnknownConversation() {
        when(conversationRepository.existsById("missing")).thenReturn(false);

        ChatMessage input = new ChatMessage().role(ChatMessage.RoleEnum.USER).content("hello");

        assertThatThrownBy(() -> sut.addMessage("missing", input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown conversation");
    }
}