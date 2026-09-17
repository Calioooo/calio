package com.calio.calendar.vote.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteRoomRetentionMigrationTest {

    @Test
    @DisplayName("Account 삭제는 VoteRoom을 보존하고 VoteRoom 삭제는 참여자와 Vote를 cascade한다")
    void givenVoteRoomWithParticipantAndVote_whenDeleteAccountThenVoteRoom_thenPreservesAndCascadesAsExpected()
            throws Exception {
        String url = "jdbc:h2:mem:vote-room-retention-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            dropH2StatusCheckConstraint(connection);
            long accountId = insertAccount(connection);
            long voteRoomId = insertVoteRoom(connection, accountId);
            long participantId = insertParticipant(connection, voteRoomId);
            insertVote(connection, participantId);

            deleteById(connection, "accounts", accountId);

            assertThat(createdByAccountId(connection, voteRoomId)).isNull();
            assertThat(count(connection, "vote_participants")).isOne();
            assertThat(count(connection, "votes")).isOne();

            deleteById(connection, "vote_rooms", voteRoomId);

            assertThat(count(connection, "vote_rooms")).isZero();
            assertThat(count(connection, "vote_participants")).isZero();
            assertThat(count(connection, "votes")).isZero();
        }
    }

    private long insertAccount(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into accounts (created_at, updated_at) values (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private void dropH2StatusCheckConstraint(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table vote_participants drop constraint ck_vote_participant_status");
        }
    }

    private long insertVoteRoom(Connection connection, long accountId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into vote_rooms (
                    public_id, name, candidate_start_date, candidate_end_date,
                    created_by_account_id, created_at, updated_at
                ) values (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setBytes(1, uuidBytes(UUID.randomUUID()));
            statement.setString(2, "여행 일정");
            statement.setObject(3, LocalDate.of(2026, 8, 14));
            statement.setObject(4, LocalDate.of(2026, 8, 20));
            statement.setLong(5, accountId);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private long insertParticipant(Connection connection, long voteRoomId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into vote_participants (
                    vote_room_id, nickname, password_hash, status, created_at, updated_at
                ) values (?, 'calio', 'hashed-password', 'SUBMITTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, voteRoomId);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private void insertVote(Connection connection, long participantId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into votes (vote_participant_id, unavailable_date, created_at, updated_at)
                values (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            statement.setLong(1, participantId);
            statement.setObject(2, LocalDate.of(2026, 8, 15));
            statement.executeUpdate();
        }
    }

    private long generatedId(PreparedStatement statement) throws Exception {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            assertThat(generatedKeys.next()).isTrue();
            return generatedKeys.getLong(1);
        }
    }

    private void deleteById(Connection connection, String tableName, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from " + tableName + " where id = ?"
        )) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    private Long createdByAccountId(Connection connection, long voteRoomId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select created_by_account_id from vote_rooms where id = ?"
        )) {
            statement.setLong(1, voteRoomId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                long accountId = resultSet.getLong("created_by_account_id");
                return resultSet.wasNull() ? null : accountId;
            }
        }
    }

    private long count(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from " + tableName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
