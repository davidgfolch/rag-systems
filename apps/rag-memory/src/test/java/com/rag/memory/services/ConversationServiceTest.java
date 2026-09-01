package com.rag.memory.services;

import com.rag.contract.model.ChatMessage;
import com.rag.memory.domain.Conversation;
import com.rag.memory.repositories.ConversationRepository;
import com.rag.memory.repositories.MessageRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
    void addsMessageToExistingConversation() {
        when(conversationRepository.findById("c1")).thenReturn(
                Optional.of(new Conversation("c1", "title", OffsetDateTime.now())));
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
        when(conversationRepository.findById("missing")).thenReturn(Optional.empty());

        ChatMessage input = new ChatMessage().role(ChatMessage.RoleEnum.USER).content("hello");

        assertThatThrownBy(() -> sut.addMessage("missing", input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown conversation");
    }
}