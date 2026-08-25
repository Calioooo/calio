package com.calio.calendar.groupcalendar.sharing.event.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PersonalEventGroupShareMigrationTest {

    @Test
    @DisplayName("개인 단건 일정 공유 migration은 원본 일정·Group Space FK와 중복 공유 제약을 정의한다")
    void migrationDefinesPersonalEventGroupSharePersistenceContract() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V21__personal_event_group_shares.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("CREATE TABLE personal_event_group_shares")
                .contains("uk_personal_event_group_share_event_group UNIQUE (event_id, group_space_id)")
                .contains("FOREIGN KEY (event_id) REFERENCES events (id)")
                .contains("FOREIGN KEY (group_space_id) REFERENCES group_spaces (id)");
    }

    @Test
    @DisplayName("개인 단건 일정 공유 migration은 원본 상세 공개 설정만 추가한다")
    void migrationAddsPersonalEventGroupSharePrivacySetting() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V22__personal_event_group_share_overrides.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("show_original_details BOOLEAN NOT NULL DEFAULT FALSE")
                .doesNotContain("override_title")
                .doesNotContain("override_start_at")
                .doesNotContain("override_end_at")
                .doesNotContain("override_all_day");
    }
}
