package com.calio.calendar.singleevent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacyEventRecurrenceMigrationTest {

  @Test
  @DisplayName("V40은 legacy recurrence Event와 그 공유 레코드를 정리한 뒤 recurrence_id를 제거한다")
  void givenLegacyRecurrenceEventWithShare_whenMigrateToV40_thenRemovesDependentRowsBeforeColumn()
      throws Exception {
    String url = "jdbc:h2:mem:legacy-event-recurrence-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    migrateTo(url, "39");
    insertLegacyRecurrenceEventWithShare(url);

    migrateTo(url, "40");

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      assertThat(rowCount(connection, "events")).isZero();
      assertThat(rowCount(connection, "personal_event_group_shares")).isZero();
      assertThat(hasColumn(connection, "EVENTS", "RECURRENCE_ID")).isFalse();
    }
  }

  private void migrateTo(String url, String target) {
    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion(target))
        .load()
        .migrate();
  }

  private void insertLegacyRecurrenceEventWithShare(String url) throws Exception {
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO accounts (id, created_at, updated_at)
          VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      statement.executeUpdate(
          """
          INSERT INTO group_spaces (id, owner_account_id, name, emoji, created_at, updated_at)
          VALUES (1, 1, 'legacy share', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
      statement.executeUpdate(
          """
          INSERT INTO events (
              id, title, description, start_at, end_at, important_event, recurrence_id,
              account_id, tag_id, created_at, updated_at, all_day, time_zone
          )
          VALUES (
              1, 'legacy occurrence', NULL, CURRENT_TIMESTAMP,
              DATEADD('HOUR', 1, CURRENT_TIMESTAMP), FALSE, 100,
              1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, 'UTC'
          )
          """);
      statement.executeUpdate(
          """
          INSERT INTO personal_event_group_shares (
              event_id, group_space_id, public_share_id, created_at, updated_at
          )
          VALUES (1, 1, '00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          """);
    }
  }

  private long rowCount(Connection connection, String tableName) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
      assertThat(resultSet.next()).isTrue();
      return resultSet.getLong(1);
    }
  }

  private boolean hasColumn(Connection connection, String tableName, String columnName)
      throws Exception {
    DatabaseMetaData metadata = connection.getMetaData();
    try (ResultSet resultSet = metadata.getColumns(null, null, tableName, columnName)) {
      return resultSet.next();
    }
  }
}
