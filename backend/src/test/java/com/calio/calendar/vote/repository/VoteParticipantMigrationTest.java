package com.calio.calendar.vote.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteParticipantMigrationTest {

    @Test
    @DisplayName("V25는 VoteParticipant와 날짜 Vote의 제약 및 VoteRoom cascade schema를 생성한다")
    void givenVoteRoomSchema_whenMigrate_thenCreatesParticipantAndVoteConstraints() throws Exception {
        // given
        String url = "jdbc:h2:mem:vote-participant-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";

        // when
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "VOTE_PARTICIPANTS"))
                    .contains("VOTE_ROOM_ID", "NICKNAME", "PASSWORD_HASH", "STATUS");
            assertThat(columnNames(connection, "VOTES"))
                    .contains("VOTE_PARTICIPANT_ID", "UNAVAILABLE_DATE");
            assertThat(isNullable(connection, "VOTE_PARTICIPANTS", "PASSWORD_HASH")).isTrue();
            assertThat(isNullable(connection, "VOTE_PARTICIPANTS", "NICKNAME")).isFalse();
            assertThat(isNullable(connection, "VOTES", "UNAVAILABLE_DATE")).isFalse();
            assertThat(uniqueConstraintNames(connection, "VOTE_PARTICIPANTS"))
                    .contains("UK_VOTE_PARTICIPANT_ROOM_NICKNAME");
            assertThat(uniqueConstraintNames(connection, "VOTES"))
                    .contains("UK_VOTE_PARTICIPANT_UNAVAILABLE_DATE");
            assertThat(importedKeyDeleteRule(connection, "VOTE_PARTICIPANTS", "VOTE_ROOMS"))
                    .isEqualTo((short) DatabaseMetaData.importedKeyCascade);
            assertThat(importedKeyDeleteRule(connection, "VOTES", "VOTE_PARTICIPANTS"))
                    .isEqualTo((short) DatabaseMetaData.importedKeyCascade);
        }
    }

    @Test
    @DisplayName("V25는 허용되지 않은 VoteParticipant 상태 저장을 거부한다")
    void givenInvalidVoteParticipantStatus_whenInsert_thenRejectsCheckConstraint() throws Exception {
        // given
        String url = "jdbc:h2:mem:vote-participant-status-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            long voteRoomId = insertVoteRoom(connection);

            // when, then
            assertThatThrownBy(() -> insertParticipant(connection, voteRoomId, "INVALID"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("CK_VOTE_PARTICIPANT_STATUS");
        }
    }

    private long insertVoteRoom(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO vote_rooms (
                    public_id,
                    name,
                    candidate_start_date,
                    candidate_end_date,
                    created_at,
                    updated_at
                ) VALUES (RANDOM_UUID(), '여행 일정', DATE '2026-08-14', DATE '2026-08-20', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                assertThat(generatedKeys.next()).isTrue();
                return generatedKeys.getLong(1);
            }
        }
    }

    private void insertParticipant(Connection connection, long voteRoomId, String status) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO vote_participants (
                    vote_room_id,
                    nickname,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, 'calio', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            statement.setLong(1, voteRoomId);
            statement.setString(2, status);
            statement.executeUpdate();
        }
    }

    private Set<String> columnNames(Connection connection, String tableName) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> columns = new HashSet<>();
        try (ResultSet resultSet = metadata.getColumns(null, null, tableName, null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private boolean isNullable(Connection connection, String tableName, String columnName) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getColumns(null, null, tableName, columnName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        }
    }

    private Set<String> uniqueConstraintNames(Connection connection, String tableName) throws Exception {
        Set<String> constraintNames = new HashSet<>();
        try (var statement = connection.prepareStatement("""
                SELECT CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_NAME = ?
                  AND CONSTRAINT_TYPE = 'UNIQUE'
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    constraintNames.add(resultSet.getString("CONSTRAINT_NAME"));
                }
            }
        }
        return constraintNames;
    }

    private short importedKeyDeleteRule(Connection connection, String tableName, String parentTableName)
            throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getImportedKeys(null, null, tableName)) {
            while (resultSet.next()) {
                if (parentTableName.equals(resultSet.getString("PKTABLE_NAME"))) {
                    return resultSet.getShort("DELETE_RULE");
                }
            }
        }
        throw new IllegalStateException("Foreign key not found for " + tableName);
    }
}
