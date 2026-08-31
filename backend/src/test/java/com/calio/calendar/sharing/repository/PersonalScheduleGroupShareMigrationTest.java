package com.calio.calendar.sharing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PersonalScheduleGroupShareMigrationTest {

    @Test
    @DisplayName("공유 mapping migration은 UUID와 source-target unique 제약을 두고 익명 정책은 membership에 둔다")
    void createsMinimalMappingSchemaWithoutCascadeOrPresentationOverrides() throws IOException {
        String migration = new ClassPathResource("db/migration/V21__personal_schedule_group_shares.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("personal_event_group_shares")
                .contains("personal_recurrence_group_shares")
                .contains("ALTER TABLE group_members")
                .contains("ADD COLUMN is_anonymous BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("public_share_id")
                .contains("UNIQUE (event_id, group_space_id)")
                .contains("UNIQUE (recurrence_event_id, group_space_id)")
                .doesNotContain("ix_personal_event_group_share_event")
                .doesNotContain("ix_personal_recurrence_group_share_event")
                .doesNotContain("ON DELETE CASCADE")
                .doesNotContain("show_original_details")
                .doesNotContain("override_title")
                .doesNotContain("selected_origin");
    }
}
