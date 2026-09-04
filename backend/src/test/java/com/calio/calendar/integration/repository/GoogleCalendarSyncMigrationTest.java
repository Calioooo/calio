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
    @DisplayName("V12는 기존 Event를 timed로 backfill하고 lease와 mapping 제약을 추가한다")
    void givenPreGoogleSyncData_whenMigrateToV12_thenAppliesGoogleCalendarSyncSchema()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-sync-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("8"));
        insertLegacyEventAndIntegration(url);

        // when
        migrateTo(url, MigrationVersion.fromVersion("12"));

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
    @DisplayName("V13 upgrade는 기존 provider/cursor/canonical data를 보존하고 recurrence mapping schema를 추가한다")
    void givenV12Data_whenMigrateToV13_thenAddsRecurrenceFoundationWithoutChangingData()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-recurrence-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("8"));
        insertLegacyEventAndIntegration(url);
        migrateTo(url, MigrationVersion.fromVersion("12"));
        insertGoogleProviderAndRecurrenceData(url);

        // when
        migrateTo(url, MigrationVersion.fromVersion("13"));

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
    @DisplayName("clean migration은 V13 recurrence provider mapping table을 생성한다")
    void givenEmptyDatabase_whenMigrateToV13_thenCreatesRecurrenceMappingTables()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-recurrence-clean;MODE=MySQL;DB_CLOSE_DELAY=-1";

        // when
        migrateTo(url, MigrationVersion.fromVersion("13"));

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
    @DisplayName("V14 empty schema upgrade는 nullable VARCHAR(255) events.time_zone만 추가한다")
    void givenV13EmptySchema_whenMigrateToV14_thenAddsNullableEventTimeZone()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:event-time-zone-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("13"));

        // when
        migrateTo(url, MigrationVersion.fromVersion("14"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "EVENTS")).contains("TIME_ZONE");
            assertThat(columnSize(connection, "EVENTS", "TIME_ZONE")).isEqualTo(255);
            assertThat(isNullable(connection, "EVENTS", "TIME_ZONE")).isTrue();
            assertThat(columnDefault(connection, "EVENTS", "TIME_ZONE")).isNull();
        }
    }

    @Test
    @DisplayName("V15 activation upgrade는 Google cursor만 reset하고 provider와 canonical data를 보존한다")
    void givenV14ProviderData_whenMigrateToV15_thenOnlyResetsGoogleCursor()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-recurrence-activation;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("8"));
        insertLegacyEventAndIntegration(url);
        migrateTo(url, MigrationVersion.fromVersion("14"));
        insertGoogleProviderAndRecurrenceData(url);

        // when
        migrateTo(url, MigrationVersion.fromVersion("15"));

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
    @DisplayName("clean migration은 V15 recurrence activation schema를 적용한다")
    void givenEmptyDatabase_whenMigrateToV15_thenAppliesActivationMigration()
            throws Exception {
        // given
        String url = "jdbc:h2:mem:google-calendar-recurrence-activation-clean;MODE=MySQL;DB_CLOSE_DELAY=-1";

        // when
        migrateTo(url, MigrationVersion.fromVersion("15"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_INTEGRATIONS"))
                    .contains("NEXT_SYNC_TOKEN");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_RECURRENCE_EVENT_MAPPINGS"))
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("V16은 Account sequence와 durable Google operation Job 제약 및 조회 index를 추가한다")
    void givenV15Schema_whenMigrateToV16_thenAddsDurableOperationRuntime() throws Exception {
        // given
        String url = "jdbc:h2:mem:google-operation-runtime;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("15"));

        // when
        migrateTo(url, MigrationVersion.fromVersion("16"));

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
    @DisplayName("V17은 Operation lease로 실행 소유권을 통일하고 Sync lease 컬럼을 제거한다")
    void givenV16Schema_whenMigrateToV17_thenRemovesSyncLeaseColumns() throws Exception {
        // given
        String url = "jdbc:h2:mem:google-operation-only-lease;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("16"));

        // when
        migrateTo(url, MigrationVersion.fromVersion("17"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_INTEGRATIONS"))
                    .contains(
                            "NEXT_SYNC_TOKEN",
                            "GOOGLE_OPERATION_LEASE_OWNER",
                            "GOOGLE_OPERATION_LEASE_EXPIRES_AT"
                    )
                    .doesNotContain("ACTIVE_SYNC_RUN_ID", "SYNC_LEASE_EXPIRES_AT");
        }
    }

    @Test
    @DisplayName("V18은 empty mapping deployment에 conflict 상태와 pending scope index를 추가한다")
    void givenEmptyV17Schema_whenMigrateToV18_thenAddsConflictFoundation() throws Exception {
        // given
        String url = "jdbc:h2:mem:google-mapping-conflict-foundation;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("17"));

        // when
        migrateTo(url, MigrationVersion.fromVersion("18"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_EVENT_MAPPINGS"))
                    .contains("SYNC_STATUS", "PROVIDER_ETAG")
                    .doesNotContain("PROVIDER_UPDATED_AT", "SYNCED_CONTENT_HASH");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_RECURRENCE_EVENT_MAPPINGS"))
                    .contains("SYNC_STATUS", "PROVIDER_ETAG")
                    .doesNotContain("PROVIDER_UPDATED_AT", "SYNCED_CONTENT_HASH");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_RECURRENCE_OVERRIDE_MAPPINGS"))
                    .contains("SYNC_STATUS", "PROVIDER_ETAG")
                    .doesNotContain("PROVIDER_UPDATED_AT", "SYNCED_CONTENT_HASH");
            assertThat(columnNames(connection, "GOOGLE_OPERATION_JOBS"))
                    .contains("CONFLICT_DETECTED")
                    .doesNotContain("TARGET_CONTENT_HASH");
            assertThat(indexNames(connection, "GOOGLE_OPERATION_JOBS"))
                    .contains("IDX_GOOGLE_OPERATION_JOBS_PENDING_SCOPE");
        }
    }

    @Test
    @DisplayName("V19는 Google operation Job의 target payload 이름을 적용한다")
    void givenV18Schema_whenMigrateToV19_thenRenamesTargetPayload() throws Exception {
        // given
        String url = "jdbc:h2:mem:google-operation-job-target-fields;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("18"));

        // when
        migrateTo(url, MigrationVersion.fromVersion("19"));

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_OPERATION_JOBS"))
                    .contains("TARGET_PAYLOAD")
                    .doesNotContain("DESIRED_PAYLOAD");
        }
    }

    @Test
    @DisplayName("V18은 mapping eTag와 status의 null 또는 잘못된 값을 거부한다")
    void givenV18Schema_whenInsertInvalidMappingState_thenRejectsIt() throws Exception {
        String url = "jdbc:h2:mem:google-mapping-conflict-constraints;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("17"));
        insertCurrentEventAndIntegration(url, false);
        migrateTo(url, MigrationVersion.fromVersion("18"));

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO google_calendar_event_mappings (
                        integration_id, event_id, calendar_key, external_event_id,
                        sync_status, provider_etag, created_at, updated_at
                    ) VALUES (900, 900, 'primary', 'missing-etag', 'ACTIVE', NULL,
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO google_calendar_event_mappings (
                        integration_id, event_id, calendar_key, external_event_id,
                        sync_status, provider_etag, created_at, updated_at
                    ) VALUES (900, 900, 'primary', 'invalid-status', 'UNKNOWN', 'etag',
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO google_calendar_event_mappings (
                        integration_id, event_id, calendar_key, external_event_id,
                        sync_status, provider_etag, created_at, updated_at
                    ) VALUES (900, 900, 'primary', 'too-long-etag', 'ACTIVE', '%s',
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted("a".repeat(1025))))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("V20은 retained integration lifecycle 상태와 credential 제약을 적용한다")
    void givenV19Schema_whenMigrateToV20_thenEnforcesRetainedIntegrationLifecycle() throws Exception {
        String url = "jdbc:h2:mem:google-retained-integration-lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("19"));
        insertCurrentEventAndIntegration(url, false);

        migrateTo(url, MigrationVersion.fromVersion("20"));

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_INTEGRATIONS"))
                    .contains("INTEGRATION_STATE", "DISCONNECTED_AT", "SYNC_ERROR_REASON", "SYNC_ERROR_AT");
            assertThat(isNullable(connection, "GOOGLE_CALENDAR_INTEGRATIONS", "ENCRYPTED_REFRESH_TOKEN"))
                    .isTrue();
            assertThatThrownBy(() -> statement.executeUpdate("""
                    UPDATE google_calendar_integrations
                    SET integration_state = 'DISCONNECTED', disconnected_at = CURRENT_TIMESTAMP
                    WHERE id = 900
                    """))
                    .isInstanceOf(SQLException.class);
            statement.executeUpdate("""
                    UPDATE google_calendar_integrations
                    SET integration_state = 'DISCONNECTED',
                        encrypted_refresh_token = NULL,
                        encrypted_access_token = NULL,
                        access_token_expires_at = NULL,
                        next_sync_token = NULL,
                        google_operation_lease_owner = NULL,
                        google_operation_lease_expires_at = NULL,
                        disconnected_at = CURRENT_TIMESTAMP
                    WHERE id = 900
                    """);
        }
    }

    @Test
    @DisplayName("V21은 기존 Google 연결을 Connection으로 보존하고 Account Integration을 분리한다")
    void givenV20ConnectedIntegration_whenMigrateToV21_thenKeepsConnectionRuntimeAndCreatesAccountIntegration()
            throws Exception {
        String url = "jdbc:h2:mem:google-calendar-integration-connection-model;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("20"));
        insertCurrentEventAndIntegration(url, true);
        updateSyncToken(url, "retained-cursor");

        migrateTo(url, MigrationVersion.fromVersion("21"));

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_INTEGRATIONS"))
                    .containsExactlyInAnyOrder("ID", "ACCOUNT_ID", "CREATED_AT", "UPDATED_AT");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_CONNECTIONS"))
                    .contains("INTEGRATION_ID", "CONNECTION_STATE", "GOOGLE_SUBJECT", "NEXT_SYNC_TOKEN")
                    .doesNotContain("ACCOUNT_ID");
            assertThat(singleString(connection, """
                    SELECT connection.google_subject
                    FROM google_calendar_connections connection
                    JOIN google_calendar_integrations integration ON integration.id = connection.integration_id
                    WHERE integration.account_id = 900
                    """)).isEqualTo("subject");
            assertThat(singleString(connection, """
                    SELECT encrypted_refresh_token
                    FROM google_calendar_connections
                    WHERE id = 900
                    """)).isEqualTo("encrypted-refresh");
            assertThat(singleString(connection, """
                    SELECT encrypted_access_token
                    FROM google_calendar_connections
                    WHERE id = 900
                    """)).isEqualTo("encrypted-access");
            assertThat(singleString(connection, """
                    SELECT connection_state
                    FROM google_calendar_connections
                    WHERE id = 900
                    """)).isEqualTo("CONNECTED");
            assertThat(singleString(connection, """
                    SELECT next_sync_token
                    FROM google_calendar_connections
                    WHERE id = 900
                    """)).isEqualTo("retained-cursor");
            assertThat(columnNames(connection, "GOOGLE_OPERATION_JOBS"))
                    .contains("CONNECTION_ID")
                    .doesNotContain("INTEGRATION_ID");
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_EVENT_MAPPINGS"))
                    .contains("CONNECTION_ID")
                    .doesNotContain("INTEGRATION_ID");
            assertThat(singleString(connection, """
                    SELECT connection_id
                    FROM google_operation_jobs
                    WHERE id = 900
                    """)).isEqualTo("900");
            assertThat(singleString(connection, """
                    SELECT connection_id
                    FROM google_calendar_event_mappings
                    WHERE id = 900
                    """)).isEqualTo("900");
        }
    }

    @Test
    @DisplayName("V22는 기존 Connection runtime을 Integration으로 보존해 옮긴다")
    void givenV21ConnectionRuntime_whenMigrateToV22_thenMovesRuntimeToIntegration() throws Exception {
        String url = "jdbc:h2:mem:google-integration-job-runtime;MODE=MySQL;DB_CLOSE_DELAY=-1";
        migrateTo(url, MigrationVersion.fromVersion("16"));
        insertV16IntegrationRuntime(url);
        migrateTo(url, MigrationVersion.fromVersion("21"));

        migrateTo(url, MigrationVersion.fromVersion("22"));

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_INTEGRATIONS")).contains(
                    "NEXT_GOOGLE_OPERATION_SEQUENCE",
                    "GOOGLE_OPERATION_LEASE_OWNER",
                    "GOOGLE_OPERATION_LEASE_EXPIRES_AT"
            );
            assertThat(columnNames(connection, "GOOGLE_CALENDAR_CONNECTIONS")).doesNotContain(
                    "NEXT_GOOGLE_OPERATION_SEQUENCE",
                    "GOOGLE_OPERATION_LEASE_OWNER",
                    "GOOGLE_OPERATION_LEASE_EXPIRES_AT"
            );
            assertThat(columnNames(connection, "GOOGLE_OPERATION_JOBS"))
                    .contains("INTEGRATION_ID", "INTEGRATION_SEQUENCE")
                    .doesNotContain("CONNECTION_ID", "ACCOUNT_SEQUENCE");
            assertThat(singleString(connection, """
                    SELECT next_google_operation_sequence
                    FROM google_calendar_integrations
                    WHERE account_id = 901
                    """)).isEqualTo("29");
            assertThat(singleString(connection, """
                    SELECT google_operation_lease_owner
                    FROM google_calendar_integrations
                    WHERE account_id = 901
                    """)).isEqualTo("worker-token");
            assertThat(singleString(connection, """
                    SELECT google_operation_lease_expires_at
                    FROM google_calendar_integrations
                    WHERE account_id = 901
                    """)).startsWith("2026-09-01 01:00:00");
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

    private void insertCurrentEventAndIntegration(String url, boolean includesJobAndMapping) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO accounts (id, created_at, updated_at)
                    VALUES (900, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO events (
                        id, title, description, start_at, end_at, important_event, all_day,
                        recurrence_id, account_id, tag_id, created_at, updated_at
                    )
                    VALUES (
                        900, 'Current', NULL, '2026-07-01 00:00:00', '2026-07-01 01:00:00',
                        FALSE, FALSE, NULL, 900, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
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
            if (includesJobAndMapping) {
                insertCurrentOperationJobAndEventMapping(statement);
            }
        }
    }

    private void insertCurrentOperationJobAndEventMapping(Statement statement) throws Exception {
        // H2 2.4 closes the in-memory database while evaluating these legacy generated/check constraints.
        statement.executeUpdate("""
                ALTER TABLE google_operation_jobs
                    DROP CONSTRAINT uk_google_operation_jobs_active_periodic_sync
                """);
        statement.executeUpdate("""
                ALTER TABLE google_operation_jobs
                    DROP COLUMN active_periodic_sync_account_id
                """);
        statement.executeUpdate("""
                ALTER TABLE google_calendar_event_mappings
                    DROP CONSTRAINT ck_google_calendar_event_mappings_sync_status
                """);
        statement.executeUpdate("""
                INSERT INTO google_operation_jobs (
                    id, operation_id, integration_id, account_id, account_sequence,
                    job_kind, job_trigger, effective_resource_scope, effective_resource_key,
                    target_payload, job_state, runnable_at, retry_count, conflict_detected, created_at, updated_at
                )
                VALUES (
                    900, 'migration-job', 900, 900, 1,
                    'EVENT_UPSERT', 'CANONICAL_MUTATION', 'EVENT', '900', '{}',
                    'PENDING', CURRENT_TIMESTAMP(6), 0, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """);
        statement.executeUpdate("""
                INSERT INTO google_calendar_event_mappings (
                    id, integration_id, event_id, calendar_key, external_event_id,
                    provider_etag, sync_status, created_at, updated_at
                )
                VALUES (
                    900, 900, 900, 'primary', 'migration-event',
                    'migration-etag', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """);
    }

    private void updateSyncToken(String url, String syncToken) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE google_calendar_integrations
                    SET next_sync_token = '%s'
                    WHERE id = 900
                    """.formatted(syncToken));
        }
    }

    private void insertV16IntegrationRuntime(String url) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO accounts (id, created_at, updated_at)
                    VALUES (901, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO google_calendar_integrations (
                        id, account_id, google_subject, google_email,
                        encrypted_refresh_token, encrypted_access_token, access_token_expires_at,
                        connected_at, next_sync_token, next_google_operation_sequence,
                        google_operation_lease_owner, google_operation_lease_expires_at,
                        created_at, updated_at
                    )
                    VALUES (
                        902, 901, 'subject-901', 'user-901@example.com',
                        'encrypted-refresh', 'encrypted-access', '2026-09-01 01:00:00',
                        '2026-09-01 00:00:00', 'cursor-901', 29,
                        'worker-token', '2026-09-01 01:00:00',
                        CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                    )
                    """);
        }
    }

    private void insertGoogleProviderAndRecurrenceData(String url) throws Exception {
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
