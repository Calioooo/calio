package com.calio.calendar.groupspace.repository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class GroupSpaceMySqlSchemaTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("calendar")
            .withUsername("calendar")
            .withPassword("calendar");

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();
    }

    @Test
    @DisplayName("MySQL은 ACTIVE nickname만 case-insensitive unique로 제한하고 inactive nickname은 재사용한다")
    void activeNicknameGeneratedColumnUsesMySqlUniqueNullSemantics() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        ); Statement statement = connection.createStatement()) {
            insertAccounts(statement, 4);
            statement.executeUpdate("""
                    INSERT INTO group_spaces
                        (name, emoji, owner_account_id, created_at, updated_at)
                    VALUES ('Team', NULL, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            insertMember(statement, 1, "Calio", "ACTIVE");

            assertThatThrownBy(() -> insertMember(statement, 2, "calio", "ACTIVE"))
                    .isInstanceOf(SQLException.class);
            assertThatCode(() -> insertMember(statement, 3, "calio", "LEFT"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> insertMember(statement, 4, "CALIO", "REMOVED"))
                    .doesNotThrowAnyException();
        }
    }

    private static void insertAccounts(Statement statement, int count) throws SQLException {
        for (int index = 0; index < count; index++) {
            statement.executeUpdate("""
                    INSERT INTO accounts (created_at, updated_at)
                    VALUES (CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
        }
    }

    private static void insertMember(
            Statement statement,
            long accountId,
            String nickname,
            String status
    ) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO group_members
                    (group_space_id, account_id, nickname, status, created_at, updated_at)
                VALUES (1, %d, '%s', '%s', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """.formatted(accountId, nickname, status));
    }
}
