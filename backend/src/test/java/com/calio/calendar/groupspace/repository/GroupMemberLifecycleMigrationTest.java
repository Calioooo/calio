package com.calio.calendar.groupspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GroupMemberLifecycleMigrationTest {

    @Test
    @DisplayName("lifecycle migration은 BaseEntity updated_at을 유지하고 status_changed_at을 보존한다")
    void migrationAddsStatusChangedAtWithoutDroppingBaseEntityTimestamps() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V11__group_member_lifecycle_timestamps.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("ADD COLUMN status_changed_at DATETIME(6) NULL")
                .contains("SET status_changed_at = updated_at")
                .contains("MODIFY COLUMN status_changed_at DATETIME(6) NOT NULL")
                .doesNotContain("DROP COLUMN created_at")
                .doesNotContain("DROP COLUMN updated_at");
    }
}
