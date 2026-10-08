package com.calio.calendar.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurrenceOverrideValuesMigrationTest {

  @Test
  @DisplayName("VO 전환은 활성·제외 변경 값을 보존하고 시리즈와 원래 시작값의 중복을 거부한다")
  void migrationPreservesValuesAndCanonicalOccurrenceKey() throws Exception {
    String url = "jdbc:h2:mem:recurrence-override-values-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .target("42")
        .load()
        .migrate();
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO accounts (id, created_at, updated_at) VALUES (900, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
      statement.executeUpdate(
          """
          INSERT INTO recurrence_events (id, recurrence_title, account_id, tag_id,
            first_occurrence_start_at, first_occurrence_end_at, all_day, time_zone,
            recurrence_rule, created_at, updated_at)
          VALUES (900, 'Series', 900, (SELECT MIN(id) FROM tags),
            TIMESTAMP '2027-01-01 09:00:00', TIMESTAMP '2027-01-01 10:00:00', FALSE, 'UTC',
            '["RRULE:FREQ=DAILY"]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      statement.executeUpdate(
          """
          INSERT INTO recurrence_event_overrides (recurrence_id, origin_start_at,
            override_title, override_description, override_start_at, override_end_at,
            override_all_day, override_time_zone, created_at, updated_at)
          VALUES (900, TIMESTAMP '2027-01-01 09:00:00', 'Moved', 'memo',
            TIMESTAMP '2027-01-04 12:00:00', TIMESTAMP '2027-01-04 13:00:00',
            FALSE, 'UTC', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      statement.executeUpdate(
          """
          INSERT INTO recurrence_event_overrides (recurrence_id, origin_start_at,
            deleted_at, created_at, updated_at)
          VALUES (900, TIMESTAMP '2027-01-02 09:00:00',
            TIMESTAMP '2027-01-01 00:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
    }

    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      try (ResultSet rows =
          statement.executeQuery(
              "SELECT * FROM recurrence_event_overrides ORDER BY origin_start_at")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getLong("recurrence_id")).isEqualTo(900);
        assertThat(rows.getString("override_title")).isEqualTo("Moved");
        assertThat(rows.getString("override_description")).isEqualTo("memo");
        assertThat(rows.getTimestamp("override_start_at").toLocalDateTime())
            .hasToString("2027-01-04T12:00");
        assertThat(rows.getTimestamp("override_end_at").toLocalDateTime())
            .hasToString("2027-01-04T13:00");
        assertThat(rows.getBoolean("override_all_day")).isFalse();
        assertThat(rows.getString("override_time_zone")).isEqualTo("UTC");
        assertThat(rows.getTimestamp("deleted_at")).isNull();
        assertThat(rows.next()).isTrue();
        assertThat(rows.getTimestamp("deleted_at").toLocalDateTime())
            .hasToString("2027-01-01T00:00");
        assertThat(rows.getString("override_title")).isNull();
        assertThat(rows.getTimestamp("override_start_at")).isNull();
        assertThat(rows.next()).isFalse();
      }
      Set<String> columns = new HashSet<>();
      try (ResultSet metadata =
          connection.getMetaData().getColumns(null, null, "RECURRENCE_EVENT_OVERRIDES", null)) {
        while (metadata.next()) columns.add(metadata.getString("COLUMN_NAME"));
      }
      assertThat(columns).doesNotContain("OVERRIDE_ID", "CREATED_AT", "UPDATED_AT");
      assertThatThrownBy(
              () ->
                  statement.executeUpdate(
                      """
          INSERT INTO recurrence_event_overrides (recurrence_id, origin_start_at, deleted_at)
          VALUES (900, TIMESTAMP '2027-01-02 09:00:00', CURRENT_TIMESTAMP)
          """))
          .isInstanceOf(SQLException.class);
    }
  }
}
