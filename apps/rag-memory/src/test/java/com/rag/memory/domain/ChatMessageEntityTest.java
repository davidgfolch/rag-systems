package com.rag.memory.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageEntityTest {

    @Test
    void exposesFields() {
        ChatMessageEntity message = new ChatMessageEntity(
                "m1", "c1", "user", "hello", OffsetDateTime.parse("2026-01-01T10:00:00+01:00"));
        assertThat(message.getId()).isEqualTo("m1");
        assertThat(message.getConversationId()).isEqualTo("c1");
        assertThat(message.getRole()).isEqualTo("user");
        assertThat(message.getContent()).isEqualTo("hello");
        assertThat(message.getCreatedAt()).isEqualTo("2026-01-01T10:00:00+01:00");
    }

    @Test
    void exposesFieldsUnsetByJpaNoArgConstructor() {
        ChatMessageEntity message = new ChatMessageEntity();
        assertThat(message.getId()).isNull();
        assertThat(message.getConversationId()).isNull();
        assertThat(message.getRole()).isNull();
        assertThat(message.getContent()).isNull();
        assertThat(message.getCreatedAt()).isNull();
    }
}