package com.calio.calendar.groupcalendar.sharing.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PersonalRecurrenceGroupShareMigrationTest {

    @Test
    @DisplayName("개인 반복 일정 공유 migration은 반복 마스터와 Group Space의 고유 매핑만 정의한다")
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
                .doesNotContain("share_scope")
                .doesNotContain("personal_recurrence_group_share_selected_origins")
                .doesNotContain("personal_recurrence_group_share_occurrence_overrides");
    }
}
