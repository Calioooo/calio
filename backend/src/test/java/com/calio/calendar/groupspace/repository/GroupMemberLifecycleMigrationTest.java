package com.calio.calendar.groupspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GroupMemberLifecycleMigrationTest {

    @Test
    @DisplayName("lifecycle migration은 기존 updated_at 값을 status_changed_at으로 보존한다")
    void migrationRenamesUpdatedAtWithoutDroppingCreatedAt() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V11__group_member_lifecycle_timestamps.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("RENAME COLUMN updated_at TO status_changed_at")
                .doesNotContain("DROP COLUMN created_at");
    }
}
