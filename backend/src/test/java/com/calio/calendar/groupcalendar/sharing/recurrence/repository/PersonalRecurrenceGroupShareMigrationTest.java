package com.calio.calendar.groupcalendar.sharing.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PersonalRecurrenceGroupShareMigrationTest {

    @Test
    @DisplayName("개인 반복 일정 공유 migration은 원본·Group Space·선택 회차 FK와 중복 제약을 정의한다")
    void migrationDefinesPersonalRecurrenceGroupSharePersistenceContract() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V23__personal_recurrence_group_shares.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("CREATE TABLE personal_recurrence_group_shares")
                .contains("UNIQUE (recurrence_event_id, group_space_id)")
                .contains("FOREIGN KEY (recurrence_event_id) REFERENCES recurrence_events (id)")
                .contains("FOREIGN KEY (group_space_id) REFERENCES group_spaces (id)")
                .contains("CREATE TABLE personal_recurrence_group_share_selected_origins")
                .contains("UNIQUE (share_id, origin_start_at)")
                .contains("CREATE TABLE personal_recurrence_group_share_occurrence_overrides")
                .contains("ON DELETE CASCADE");
    }
}
