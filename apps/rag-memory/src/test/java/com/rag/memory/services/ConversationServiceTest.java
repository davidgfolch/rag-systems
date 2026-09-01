package com.rag.memory.services;

import com.rag.contract.model.ChatMessageDTO;
import com.rag.contract.model.ConversationDTO;
import com.rag.memory.domain.ChatMessageEntity;
import com.rag.memory.domain.ConversationEntity;
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
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConversationDTO result = sut.createConversation("   ");

        assertThat(result.getTitle()).isEqualTo("New conversation");
        assertThat(result.getId()).isNotBlank();
    }

    @Test
    void createsConversationWithTrimmedTitle() {
        when(conversationRepository.save(any(ConversationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConversationDTO result = sut.createConversation("  My chat  ");

        assertThat(result.getTitle()).isEqualTo("My chat");
    }

    @Test
    void listsConversationsNewestFirst() {
        ConversationEntity older = new ConversationEntity("c1", "old", OffsetDateTime.now().minusDays(1));
        ConversationEntity newer = new ConversationEntity("c2", "new", OffsetDateTime.now());
        when(conversationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(newer, older));

        List<ConversationDTO> result = sut.listConversations();

        assertThat(result).extracting(ConversationDTO::getId)
                .containsExactly("c2", "c1");
    }

    @Test
    void listsMessagesForConversation() {
        ChatMessageEntity stored = new ChatMessageEntity(
                "m1", "c1", "user", "hello", OffsetDateTime.parse("2026-01-01T10:00:00+01:00"));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc("c1")).thenReturn(List.of(stored));

        List<ChatMessageDTO> result = sut.listMessages("c1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("m1");
        assertThat(result.get(0).getRole()).isEqualTo(ChatMessageDTO.RoleEnum.USER);
    }

    @Test
    void addsMessageToExistingConversation() {
        when(conversationRepository.existsById("c1")).thenReturn(true);
        when(messageRepository.save(any(ChatMessageEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageDTO input = new ChatMessageDTO().role(ChatMessageDTO.RoleEnum.USER).content("hello");
        ChatMessageDTO result = sut.addMessage("c1", input);

        assertThat(result.getConversationId()).isEqualTo("c1");
        assertThat(result.getRole()).isEqualTo(ChatMessageDTO.RoleEnum.USER);
        verify(messageRepository).save(any(ChatMessageEntity.class));
    }

    @Test
    void rejectsMessageToUnknownConversation() {
        when(conversationRepository.existsById("missing")).thenReturn(false);

        ChatMessageDTO input = new ChatMessageDTO().role(ChatMessageDTO.RoleEnum.USER).content("hello");

        assertThatThrownBy(() -> sut.addMessage("missing", input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown conversation");
    }
}