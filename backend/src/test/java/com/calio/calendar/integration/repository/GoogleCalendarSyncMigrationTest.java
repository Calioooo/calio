package com.calio.calendar.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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

    @Test
    @DisplayName("V10 upgrade는 기존 provider/cursor/canonical data를 보존하고 recurrence mapping schema를 추가한다")
    void givenV9Data_whenMigrateToV10_thenAddsRecurrenceFoundationWithoutChangingData()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-recurrence-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("8"));
        insertLegacyEventAndIntegration(url);
        migrateTo(url, MigrationVersion.fromVersion("9"));
        insertV9ProviderAndRecurrenceData(url);

        // when
        migrateTo(url, MigrationVersion.fromVersion("10"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnSize(
                    connection,
                    "GOOGLE_CALENDAR_EVENT_MAPPINGS",
                    "PROVIDER_ETAG"
            )).isEqualTo(1024);
            assertThat(singleString(
                    connection,
                    "SELECT provider_etag FROM google_calendar_event_mappings WHERE id = 900"
            )).isEqualTo("existing-etag");
            assertThat(singleString(
                    connection,
                    "SELECT next_sync_token FROM google_calendar_integrations WHERE id = 900"
            )).isEqualTo("existing-cursor");
            assertThat(singleString(
                    connection,
                    "SELECT recurrence_title FROM recurrence_events WHERE id = 901"
            )).isEqualTo("Existing recurrence");
            assertThat(columnNames(
                    connection,
                    "GOOGLE_CALENDAR_RECURRENCE_EVENT_MAPPINGS"
            )).contains(
                    "INTEGRATION_ID",
                    "RECURRENCE_EVENT_ID",
                    "CALENDAR_KEY",
                    "EXTERNAL_EVENT_ID",
                    "PROVIDER_ETAG",
                    "PROVIDER_UPDATED_AT"
            );
            assertThat(columnNames(
                    connection,
                    "GOOGLE_CALENDAR_RECURRENCE_OVERRIDE_MAPPINGS"
            )).doesNotContain("INTEGRATION_ID", "CALENDAR_KEY", "ORIGIN_START_AT");
        }
    }

    @Test
    @DisplayName("clean migration은 V10 recurrence provider mapping table을 생성한다")
    void givenEmptyDatabase_whenMigrateToV10_thenCreatesRecurrenceMappingTables()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-recurrence-clean;MODE=MySQL;DB_CLOSE_DELAY=-1";

        // when
        migrateTo(url, MigrationVersion.fromVersion("10"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(
                    connection,
                    "GOOGLE_CALENDAR_RECURRENCE_EVENT_MAPPINGS"
            )).isNotEmpty();
            assertThat(columnNames(
                    connection,
                    "GOOGLE_CALENDAR_RECURRENCE_OVERRIDE_MAPPINGS"
            )).isNotEmpty();
        }
    }

    @Test
    @DisplayName("V10 empty schema upgrade는 nullable VARCHAR(255) events.time_zone만 추가한다")
    void givenV10EmptySchema_whenMigrateToV11_thenAddsNullableEventTimeZone()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:event-time-zone-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("10"));

        // when
        migrateTo(url, MigrationVersion.fromVersion("11"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "EVENTS")).contains("TIME_ZONE");
            assertThat(columnSize(connection, "EVENTS", "TIME_ZONE")).isEqualTo(255);
            assertThat(isNullable(connection, "EVENTS", "TIME_ZONE")).isTrue();
            assertThat(columnDefault(connection, "EVENTS", "TIME_ZONE")).isNull();
        }
    }

    @Test
    @DisplayName("V12 activation upgrade는 Google cursor만 reset하고 provider와 canonical data를 보존한다")
    void givenV11ProviderData_whenMigrateToV12_thenOnlyResetsGoogleCursor()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-recurrence-activation;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("8"));
        insertLegacyEventAndIntegration(url);
        migrateTo(url, MigrationVersion.fromVersion("11"));
        insertV9ProviderAndRecurrenceData(url);

        // when
        migrateTo(url, MigrationVersion.fromVersion("12"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(singleString(
                    connection,
                    "SELECT next_sync_token FROM google_calendar_integrations WHERE id = 900"
            )).isNull();
            assertThat(singleString(
                    connection,
                    "SELECT provider_etag FROM google_calendar_event_mappings WHERE id = 900"
            )).isEqualTo("existing-etag");
            assertThat(singleString(
                    connection,
                    "SELECT title FROM events WHERE id = 900"
            )).isEqualTo("Legacy");
            assertThat(singleString(
                    connection,
                    "SELECT recurrence_title FROM recurrence_events WHERE id = 901"
            )).isEqualTo("Existing recurrence");
        }
    }

    @Test
    @DisplayName("clean migration은 V12 recurrence activation schema를 적용한다")
    void givenEmptyDatabase_whenMigrateToV12_thenAppliesActivationMigration()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-recurrence-activation-clean;MODE=MySQL;DB_CLOSE_DELAY=-1";

        // when
        migrateTo(url, MigrationVersion.fromVersion("12"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_INTEGRATIONS"))
                    .contains("NEXT_SYNC_TOKEN");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_RECURRENCE_EVENT_MAPPINGS"))
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("V13은 Account sequence와 durable Google operation Job 제약 및 조회 index를 추가한다")
    void givenV12Schema_whenMigrateToV13_thenAddsDurableOperationRuntime() throws Exception {
        // given
        String url = "jdbc:h2:mem:google-operation-runtime;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("12"));

        // when
        migrateTo(url, MigrationVersion.fromVersion("13"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_INTEGRATIONS")).contains(
                    "NEXT_GOOGLE_OPERATION_SEQUENCE",
                    "GOOGLE_OPERATION_LEASE_OWNER",
                    "GOOGLE_OPERATION_LEASE_EXPIRES_AT"
            );
            assertThat(columnNames(connection, "ACCOUNTS")).doesNotContain(
                    "NEXT_GOOGLE_OPERATION_SEQUENCE",
                    "GOOGLE_OPERATION_LEASE_OWNER",
                    "GOOGLE_OPERATION_LEASE_EXPIRES_AT"
            );
            assertThat(columnNames(connection, "GOOGLE_OPERATION_JOBS")).contains(
                    "OPERATION_ID", "INTEGRATION_ID", "ACCOUNT_ID", "ACCOUNT_SEQUENCE",
                    "JOB_KIND", "JOB_TRIGGER", "JOB_STATE", "RUNNABLE_AT", "RETRY_COUNT",
                    "OWNER_TOKEN", "TERMINAL_REASON", "TERMINAL_AT"
            );
            assertThat(indexNames(connection, "GOOGLE_OPERATION_JOBS")).contains(
                    "IDX_GOOGLE_OPERATION_JOBS_ACCOUNT_HEAD",
                    "IDX_GOOGLE_OPERATION_JOBS_RUNNABLE",
                    "IDX_GOOGLE_OPERATION_JOBS_TERMINAL_CLEANUP"
            );
        }
    }

    @Test
    @DisplayName("V14는 빈 매핑에도 마지막 동기화 내용과 충돌 기록 제약을 추가한다")
    void givenV13EmptyMappings_whenMigrateToV14_thenAddsConflictFoundation() throws Exception {
        // given
        String url = "jdbc:h2:mem:google-mapping-conflict-foundation;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("13"));
        // when
        migrateTo(url, MigrationVersion.fromVersion("14"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_EVENT_MAPPINGS"))
                    .contains("SYNC_STATUS", "SYNCED_CONTENT_HASH");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_RECURRENCE_EVENT_MAPPINGS"))
                    .contains("SYNC_STATUS", "SYNCED_CONTENT_HASH");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_RECURRENCE_OVERRIDE_MAPPINGS"))
                    .contains("SYNC_STATUS", "SYNCED_CONTENT_HASH");
            assertThat(columnNames(connection, "GOOGLE_OPERATION_JOBS"))
                    .contains("CONFLICT_DETECTED", "DESIRED_CONTENT_HASH");
            assertThat(columnSize(
                    connection,
                    "GOOGLE_OPERATION_JOBS",
                    "EFFECTIVE_RESOURCE_KEY"
            )).isEqualTo(128);
            assertThat(indexNames(connection, "GOOGLE_OPERATION_JOBS"))
                    .contains("IDX_GOOGLE_OPERATION_JOBS_PENDING_SCOPE");
        }
    }

    @Test
    @DisplayName("V14는 Google 쓰기 해시와 매핑 동기화 값이 잘못되면 DB에서 거부한다")
    void givenInvalidConflictFoundationValues_whenPersisting_thenConstraintsRejectThem()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-mapping-conflict-constraints;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            migrateTo(url, MigrationVersion.fromVersion("14"));

            // when, then
            removeH2GeneratedPeriodicScope(connection);
            insertV14ConstraintFixtures(connection);
            insertSyncJobWithNullHash(connection);
            assertThat(singleLong(
                    connection,
                    "SELECT COUNT(*) FROM google_operation_jobs WHERE id = 919"
            )).isOne();
            assertThatThrownBy(() -> insertOutboundJob(
                    connection, 920L, null
            )).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertOutboundJob(
                    connection, 921L, "invalid-hash"
            )).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertEventMapping(
                    connection, "INVALID", validHash()
            )).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertEventMapping(
                    connection, "ACTIVE", null
            )).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertEventMapping(
                    connection, "ACTIVE", "invalid-hash"
            )).isInstanceOf(SQLException.class);
        }
    }

    private void removeH2GeneratedPeriodicScope(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    ALTER TABLE google_operation_jobs
                    DROP CONSTRAINT uk_google_operation_jobs_active_periodic_sync
                    """);
            statement.executeUpdate("""
                    ALTER TABLE google_operation_jobs
                    DROP COLUMN active_periodic_sync_account_id
                    """);
        }
    }

    private void insertSyncJobWithNullHash(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO google_operation_jobs (
                        id, operation_id, integration_id, account_id, account_sequence,
                        job_kind, job_trigger, effective_resource_scope,
                        effective_resource_key, desired_content_hash,
                        job_state, runnable_at, retry_count, created_at, updated_at
                    )
                    VALUES (
                        919, 'constraint-sync-operation', 920, 920, 919,
                        'SYNC', 'MANUAL', 'PRIMARY_CALENDAR', 'primary', NULL,
                        'PENDING', CURRENT_TIMESTAMP(6), 0,
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
        }
    }

    private void insertV14ConstraintFixtures(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO accounts (id, created_at, updated_at)
                    VALUES (920, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO google_calendar_integrations (
                        id, account_id, google_subject, google_email,
                        encrypted_refresh_token, encrypted_access_token,
                        access_token_expires_at, connected_at, created_at, updated_at
                    )
                    VALUES (
                        920, 920, 'constraint-subject', 'constraint@example.com',
                        'encrypted-refresh', 'encrypted-access',
                        '2026-08-05 01:00:00', '2026-08-05 00:00:00',
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO events (
                        id, title, description, start_at, end_at, all_day, time_zone,
                        important_event, recurrence_id, account_id, tag_id,
                        created_at, updated_at
                    )
                    VALUES (
                        920, 'Constraint event', NULL,
                        '2026-08-05 00:00:00', '2026-08-05 01:00:00', FALSE, 'UTC',
                        FALSE, NULL, 920, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
        }
    }

    private void insertOutboundJob(
            Connection connection,
            long id,
            String desiredGoogleContentHash
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO google_operation_jobs (
                    id, operation_id, integration_id, account_id, account_sequence,
                    job_kind, job_trigger, effective_resource_scope,
                    effective_resource_key, desired_payload, desired_content_hash,
                    job_state, runnable_at, retry_count, created_at, updated_at
                )
                VALUES (
                    ?, ?, 920, 920, ?, 'EVENT_UPSERT', 'CANONICAL_MUTATION',
                    'GENERAL_EVENT', '920', '{}', ?,
                    'PENDING', CURRENT_TIMESTAMP(6), 0,
                    CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """)) {
            statement.setLong(1, id);
            statement.setString(2, "constraint-operation-" + id);
            statement.setLong(3, id);
            statement.setString(4, desiredGoogleContentHash);
            statement.executeUpdate();
        }
    }

    private void insertEventMapping(
            Connection connection,
            String syncStatus,
            String syncedContentHash
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO google_calendar_event_mappings (
                    id, integration_id, event_id, calendar_key, external_event_id,
                    sync_status, synced_content_hash, created_at, updated_at
                )
                VALUES (
                    920, 920, 920, 'primary', 'constraint-event',
                    ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """)) {
            statement.setString(1, syncStatus);
            statement.setString(2, syncedContentHash);
            statement.executeUpdate();
        }
    }

    private String validHash() {
        return "v1:" + "a".repeat(64);
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

    private void insertV9ProviderAndRecurrenceData(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE google_calendar_integrations
                    SET next_sync_token = 'existing-cursor'
                    WHERE id = 900
                    """);
            statement.executeUpdate("""
                    INSERT INTO google_calendar_event_mappings (
                        id, integration_id, event_id, calendar_key, external_event_id,
                        provider_etag, provider_updated_at, created_at, updated_at
                    )
                    VALUES (
                        900, 900, 900, 'primary', 'existing-event',
                        'existing-etag', '2026-07-01 00:00:00',
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO recurrence_events (
                        id, recurrence_title, recurrence_description, all_day, time_zone,
                        first_occurrence_start_at, first_occurrence_end_at, recurrence_rule,
                        account_id, tag_id, created_at, updated_at
                    )
                    VALUES (
                        901, 'Existing recurrence', NULL, TRUE, NULL,
                        '2026-07-20 00:00:00', '2026-07-21 00:00:00',
                        '["RRULE:FREQ=DAILY"]', 900, 1,
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

    private int columnSize(
            Connection connection,
            String tableName,
            String columnName
    ) throws Exception {
        try (ResultSet resultSet = connection.getMetaData()
                .getColumns(null, null, tableName, columnName)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt("COLUMN_SIZE");
        }
    }

    private String singleString(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private long singleLong(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
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
