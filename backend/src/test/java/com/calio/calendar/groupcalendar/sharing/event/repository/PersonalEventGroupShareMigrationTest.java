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
    @DisplayName("개인 단건 일정 공유 정책 migration은 익명 노출과 공개 UUID를 추가한다")
    void migrationAddsPersonalEventGroupShareAnonymousExposureAndPublicId() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V25__simplify_personal_event_group_shares.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("is_anonymous BOOLEAN NOT NULL DEFAULT TRUE")
                .contains("public_share_id CHAR(36)")
                .contains("SET is_anonymous = NOT show_original_details")
                .contains("uk_personal_event_group_share_public_id UNIQUE (public_share_id)");
    }
}
