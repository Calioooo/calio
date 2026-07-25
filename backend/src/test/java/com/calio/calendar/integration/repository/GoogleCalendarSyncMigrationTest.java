package com.calio.calendar.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarSyncMigrationTest {

    @Test
    @DisplayName("V9는 기존 Event를 timed로 backfill하고 lease와 mapping 제약을 추가한다")
    void givenV8Data_whenMigrateToV9_thenAppliesGoogleCalendarSyncSchema() throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-sync-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("8"));
        insertLegacyEventAndIntegration(url);

        // when
        migrateTo(url, MigrationVersion.fromVersion("9"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(eventAllDay(connection)).isFalse();
            assertThat(isNullable(connection, "EVENTS", "ALL_DAY")).isFalse();
            assertThat(columnDefault(connection, "EVENTS", "ALL_DAY")).isNull();
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_INTEGRATIONS"))
                    .contains("NEXT_SYNC_TOKEN", "ACTIVE_SYNC_RUN_ID", "SYNC_LEASE_EXPIRES_AT");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_EVENT_MAPPINGS"))
                    .contains(
                            "INTEGRATION_ID",
                            "EVENT_ID",
                            "CALENDAR_KEY",
                            "EXTERNAL_EVENT_ID",
                            "PROVIDER_ETAG",
                            "PROVIDER_UPDATED_AT"
                    );
            assertThat(indexNames(connection, "EVENTS"))
                    .contains("IDX_EVENTS_ACCOUNT_START_AT");
            Set<String> mappingIndexes = indexNames(
                    connection,
                    "GOOGLE_CALENDAR_EVENT_MAPPINGS"
            );
            assertThat(mappingIndexes).anyMatch(
                    name -> name.startsWith("UK_GOOGLE_CALENDAR_MAPPING_EXTERNAL_IDENTITY")
            );
            assertThat(mappingIndexes).anyMatch(
                    name -> name.startsWith("UK_GOOGLE_CALENDAR_MAPPING_EVENT_ID")
            );
            assertThatThrownBy(() -> setIncompleteLease(connection))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void migrateTo(String url, MigrationVersion target) {
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private void insertLegacyEventAndIntegration(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO accounts (id, created_at, updated_at)
                    VALUES (900, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO events (
                        id, title, description, start_at, end_at, important_event,
                        recurrence_id, account_id, tag_id, created_at, updated_at
                    )
                    VALUES (
                        900, 'Legacy', NULL, '2026-07-01 00:00:00', '2026-07-01 01:00:00',
                        FALSE, NULL, 900, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO google_calendar_integrations (
                        id, account_id, google_subject, google_email,
                        encrypted_refresh_token, encrypted_access_token,
                        access_token_expires_at, connected_at, created_at, updated_at
                    )
                    VALUES (
                        900, 900, 'subject', 'user@example.com',
                        'encrypted-refresh', 'encrypted-access',
                        '2026-07-01 01:00:00', '2026-07-01 00:00:00',
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
        }
    }

    private boolean eventAllDay(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT all_day FROM events WHERE id = 900"
             )) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getBoolean("all_day");
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

    private boolean isNullable(
            Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        try (ResultSet resultSet = connection.getMetaData()
                .getColumns(null, null, tableName, columnName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        }
    }

    private String columnDefault(
            Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        try (ResultSet resultSet = connection.getMetaData()
                .getColumns(null, null, tableName, columnName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString("COLUMN_DEF");
        }
    }

    private Set<String> indexNames(Connection connection, String tableName) throws Exception {
        Set<String> indexes = new HashSet<>();
        try (ResultSet resultSet = connection.getMetaData()
                .getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                indexes.add(resultSet.getString("INDEX_NAME"));
            }
        }
        return indexes;
    }

    private void setIncompleteLease(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE google_calendar_integrations
                    SET active_sync_run_id = 'run-without-expiry'
                    WHERE id = 900
                    """);
        }
    }
}
