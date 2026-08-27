package com.calio.calendar.tag.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GroupTagMigrationTest {

    @Test
    @DisplayName("기존 V2 seed는 보존하고 Group Space tag 전환은 V18에서 수행한다")
    void preservesAppliedSeedMigrationAndMigratesTagTypesInNewVersion() throws IOException {
        String seedMigration = migration("V2__seed_default_tags.sql");
        String groupTagMigration = migration("V18__group_space_tags.sql");

        assertThat(seedMigration)
                .contains("SELECT 'DEFAULT'")
                .doesNotContain("PERSONAL_DEFAULT");
        assertThat(groupTagMigration)
                .contains("SET tag_type = 'PERSONAL_DEFAULT'")
                .contains("ADD COLUMN group_space_id")
                .doesNotContain("ON DELETE CASCADE");
    }

    private String migration(String filename) throws IOException {
        return new ClassPathResource("db/migration/" + filename).getContentAsString(StandardCharsets.UTF_8);
    }
}
