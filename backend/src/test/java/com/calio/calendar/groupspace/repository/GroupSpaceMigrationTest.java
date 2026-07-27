package com.calio.calendar.groupspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GroupSpaceMigrationTest {

    @Test
    @DisplayName("Group Space migration은 OWNER source of truth와 ACTIVE nickname 제약을 정의한다")
    void migrationDefinesGroupSpacePersistenceContract() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V9__group_space_core.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("owner_account_id BIGINT NOT NULL")
                .doesNotContain("UNIQUE (owner_account_id)")
                .contains("uk_group_member_group_account UNIQUE (group_space_id, account_id)")
                .contains("uk_group_member_active_nickname UNIQUE (group_space_id, active_nickname)")
                .contains("CASE WHEN status = 'ACTIVE' THEN nickname ELSE NULL END")
                .contains("ix_group_member_account_status (account_id, status)")
                .contains("ix_group_member_group_status (group_space_id, status)")
                .contains("COLLATE utf8mb4_0900_ai_ci");
    }
}
