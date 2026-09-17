package com.calio.calendar.vote.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteRoomMigrationTest {

    @Test
    @DisplayName("V24는 공개 UUID unique 제약과 Account SET NULL VoteRoom schema를 생성한다")
    void givenAccountSchema_whenMigrate_thenCreatesVoteRoomPublicIdAndCreatorConstraints() throws Exception {
        String url = "jdbc:h2:mem:vote-room-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "VOTE_ROOMS"))
                    .contains(
                            "PUBLIC_ID",
                            "NAME",
                            "CANDIDATE_START_DATE",
                            "CANDIDATE_END_DATE",
                            "CREATED_BY_ACCOUNT_ID"
                    );
            assertThat(columnType(connection, "VOTE_ROOMS", "PUBLIC_ID")).isEqualTo(Types.BINARY);
            assertThat(isNullable(connection, "VOTE_ROOMS", "CREATED_BY_ACCOUNT_ID")).isTrue();
            assertThat(uniqueConstraintNames(connection, "VOTE_ROOMS"))
                    .contains("UK_VOTE_ROOMS_PUBLIC_ID");
            assertThat(importedKeyDeleteRule(connection, "VOTE_ROOMS", "ACCOUNTS"))
                    .isEqualTo((short) DatabaseMetaData.importedKeySetNull);
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

    private int columnType(Connection connection, String tableName, String columnName) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet resultSet = metadata.getColumns(null, null, tableName, columnName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt("DATA_TYPE");
        }
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
