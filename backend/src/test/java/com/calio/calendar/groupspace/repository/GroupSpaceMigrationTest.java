package com.calio.calendar.groupspace.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class GroupSpaceMigrationTest {

    @Test
    @DisplayName("MySQL 8.4 migration은 ACTIVE nickname만 case-insensitive unique하게 제한한다")
    void givenMySql84_whenMigrate_thenActiveNicknameGeneratedColumnEnforcesContract() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker가 있는 VERIFY 환경에서 실행한다.");

        try (GenericContainer<?> mysql = mysql84()) {
            mysql.start();
            String jdbcUrl = "jdbc:mysql://%s:%d/calendar".formatted(
                    mysql.getHost(),
                    mysql.getMappedPort(3306)
            );
            migrate(jdbcUrl);
            assertActiveNicknameContract(jdbcUrl);
        }
    }

    private GenericContainer<?> mysql84() {
        return new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
                .withExposedPorts(3306)
                .withEnv("MYSQL_DATABASE", "calendar")
                .withEnv("MYSQL_USER", "calendar")
                .withEnv("MYSQL_PASSWORD", "calendar")
                .withEnv("MYSQL_ROOT_PASSWORD", "root");
    }

    private void migrate(String jdbcUrl) {
        Flyway.configure()
                .dataSource(jdbcUrl, "calendar", "calendar")
                .load()
                .migrate();
    }

    private void assertActiveNicknameContract(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "calendar", "calendar");
             Statement statement = connection.createStatement()) {
            insertAccounts(statement);
            long groupSpaceId = insertGroupSpace(statement);
            insertMember(statement, groupSpaceId, 1, "Friend", "ACTIVE");

            assertThatThrownBy(() -> insertMember(statement, groupSpaceId, 2, "friend", "ACTIVE"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_group_member_active_nickname");

            statement.executeUpdate("UPDATE group_members SET status = 'LEFT' WHERE account_id = 1");
            insertMember(statement, groupSpaceId, 2, "friend", "ACTIVE");
            assertThat(activeMemberCount(statement, groupSpaceId)).isEqualTo(1);
        }
    }

    private void insertAccounts(Statement statement) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO accounts (id, created_at, updated_at)
                VALUES (1, NOW(6), NOW(6)), (2, NOW(6), NOW(6))
                """);
    }

    private long insertGroupSpace(Statement statement) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO group_spaces (id, name, emoji, owner_account_id, created_at, updated_at)
                VALUES (1, 'Group', NULL, 1, NOW(6), NOW(6))
                """);
        return 1L;
    }

    private void insertMember(
            Statement statement,
            long groupSpaceId,
            long accountId,
            String nickname,
            String status
    ) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO group_members (
                    group_space_id, account_id, nickname, status, created_at, updated_at
                )
                VALUES (%d, %d, '%s', '%s', NOW(6), NOW(6))
                """.formatted(groupSpaceId, accountId, nickname, status));
    }

    private int activeMemberCount(Statement statement, long groupSpaceId) throws SQLException {
        try (var result = statement.executeQuery("""
                SELECT COUNT(*)
                FROM group_members
                WHERE group_space_id = %d AND status = 'ACTIVE'
                """.formatted(groupSpaceId))) {
            result.next();
            return result.getInt(1);
        }
    }
}
