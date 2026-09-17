package com.calio.calendar.aicalendar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CalendarConversationMigrationTest {

    @Test
    @DisplayName("AI calendar conversation migration은 account ownership, chronological messages와 cleanup index를 정의한다")
    void migrationDefinesConversationPersistenceContract() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V22__ai_calendar_conversations.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("CREATE TABLE ai_calendar_conversations")
                .contains("conversation_id CHAR(36) NOT NULL")
                .contains("FOREIGN KEY (account_id) REFERENCES accounts (id)")
                .contains("last_activity_at DATETIME(6) NOT NULL")
                .contains("CREATE TABLE ai_calendar_messages")
                .contains("FOREIGN KEY (conversation_id) REFERENCES ai_calendar_conversations (id) ON DELETE CASCADE")
                .contains("CHECK (message_role IN ('USER', 'ASSISTANT'))")
                .contains("ix_ai_calendar_conversations_cleanup")
                .contains("ix_ai_calendar_messages_conversation_order");
    }
}
