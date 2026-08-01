package com.calio.calendar.groupinvitation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GroupInvitationMigrationTest {

    @Test
    @DisplayName("Group Invitation migration은 hash-only storage, FK, unique와 cleanup index를 정의한다")
    void migrationDefinesInvitationPersistenceContract() throws IOException {
        // when
        String migration = new ClassPathResource("db/migration/V10__group_invitations.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        // then
        assertThat(migration)
                .contains("link_token_hash BINARY(32) NOT NULL")
                .contains("invite_code_hash BINARY(32) NOT NULL")
                .contains("FOREIGN KEY (group_space_id) REFERENCES group_spaces (id)")
                .contains("FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)")
                .contains("uk_group_invitations_link_token_hash UNIQUE (link_token_hash)")
                .contains("uk_group_invitations_invite_code_hash UNIQUE (invite_code_hash)")
                .contains("(group_space_id, created_by_member_id, expires_at)")
                .contains("ix_group_invitations_expiry (expires_at, id)")
                .doesNotContain("link_token VARCHAR")
                .doesNotContain("invite_code VARCHAR");
    }
}
