package com.rag.memory.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTest {

    @Test
    void exposesFields() {
        Conversation conversation =
                new Conversation("c1", "Title", OffsetDateTime.parse("2026-01-01T10:00:00+01:00"));

        assertThat(conversation.getId()).isEqualTo("c1");
        assertThat(conversation.getTitle()).isEqualTo("Title");
        assertThat(conversation.getCreatedAt()).isEqualTo("2026-01-01T10:00:00+01:00");
    }
}