package com.calio.calendar.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurrenceMigrationTest {

    @Test
    @DisplayName("빈 recurrence table에는 RFC 5545 master와 nullable override snapshot schema가 적용된다")
    void givenEmptyLegacyTables_whenMigrate_thenCreatesRfc5545Schema() throws Exception {
        // given
        String url = "jdbc:h2:mem:recurrence-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("8")
                .load();

        // when
        flyway.migrate();

        // then
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columnNames(connection, "RECURRENCE_EVENTS"))
                    .contains(
                            "ALL_DAY",
                            "TIME_ZONE",
                            "RECURRENCE_RULE",
                            "FIRST_OCCURRENCE_START_AT",
                            "FIRST_OCCURRENCE_END_AT"
                    )
                    .doesNotContain(
                            "RECURRENCE_FREQUENCY",
                            "RECURRENCE_LINES",
                            "RECURRENCE_START_DATE",
                            "RECURRENCE_END_DATE",
                            "RECURRENCE_START_TIME",
                            "RECURRENCE_END_TIME"
                    );
            assertThat(columnNames(connection, "RECURRENCE_EVENT_OVERRIDES"))
                    .contains(
                            "OVERRIDE_TITLE",
                            "OVERRIDE_DESCRIPTION",
                            "OVERRIDE_ALL_DAY",
                            "OVERRIDE_TIME_ZONE"
                    );
            assertThat(isNullable(connection, "RECURRENCE_EVENTS", "ALL_DAY")).isFalse();
            assertThat(isNullable(connection, "RECURRENCE_EVENTS", "RECURRENCE_RULE")).isFalse();
            assertThat(isNullable(connection, "RECURRENCE_EVENTS", "TIME_ZONE")).isTrue();
            assertThat(isNullable(connection, "RECURRENCE_EVENTS", "FIRST_OCCURRENCE_START_AT")).isFalse();
            assertThat(isNullable(connection, "RECURRENCE_EVENTS", "FIRST_OCCURRENCE_END_AT")).isFalse();
            assertThat(isNullable(connection, "RECURRENCE_EVENT_OVERRIDES", "OVERRIDE_TITLE")).isTrue();
            assertThat(isNullable(connection, "RECURRENCE_EVENT_OVERRIDES", "OVERRIDE_DESCRIPTION")).isTrue();
            assertThat(isNullable(connection, "RECURRENCE_EVENT_OVERRIDES", "OVERRIDE_ALL_DAY")).isTrue();
            assertThat(isNullable(connection, "RECURRENCE_EVENT_OVERRIDES", "OVERRIDE_TIME_ZONE")).isTrue();
            assertThat(indexNames(connection, "RECURRENCE_EVENTS"))
                    .contains("IDX_RECURRENCE_EVENTS_ACCOUNT_FIRST_OCCURRENCE")
                    .doesNotContain("IDX_RECURRENCE_EVENTS_ACCOUNT_PERIOD");
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

    private Set<String> indexNames(Connection connection, String tableName) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        Set<String> indexes = new HashSet<>();
        try (ResultSet resultSet = metadata.getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                indexes.add(resultSet.getString("INDEX_NAME"));
            }
        }
        return indexes;
    }
}
