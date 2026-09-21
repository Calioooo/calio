package com.calio.calendar.account.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountAuthTokenMigrationTest {

  @Test
  @DisplayName("V26은 기존 Account 인증 토큰을 Account embedded 컬럼으로 이관한다")
  void givenAccountAuthTokenSchema_whenMigrate_thenEmbedsTokenValuesInAccount() throws Exception {
    String url = "jdbc:h2:mem:account-auth-token-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .target("25")
        .load()
        .migrate();

    long accountId;
    Instant revokedAt = Instant.parse("2026-09-18T00:00:00Z");
    Instant lastUsedAt = Instant.parse("2026-09-19T00:00:00Z");
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      accountId = insertAccount(connection);
      insertAuthToken(connection, accountId, "token-hash", revokedAt, lastUsedAt);
    }

    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      try (PreparedStatement statement =
          connection.prepareStatement(
              """
              select auth_token_hash, auth_token_revoked_at, auth_token_last_used_at
              from accounts
              where id = ?
              """)) {
        statement.setLong(1, accountId);
        try (ResultSet resultSet = statement.executeQuery()) {
          assertThat(resultSet.next()).isTrue();
          assertThat(resultSet.getString("auth_token_hash")).isEqualTo("token-hash");
          assertThat(resultSet.getTimestamp("auth_token_revoked_at").toInstant())
              .isEqualTo(revokedAt);
          assertThat(resultSet.getTimestamp("auth_token_last_used_at").toInstant())
              .isEqualTo(lastUsedAt);
        }
      }
      assertThat(tableExists(connection, "ACCOUNT_AUTH_TOKENS")).isFalse();
    }
  }

  private long insertAccount(Connection connection) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "insert into accounts (created_at, updated_at) values (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            Statement.RETURN_GENERATED_KEYS)) {
      statement.executeUpdate();
      try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
        assertThat(generatedKeys.next()).isTrue();
        return generatedKeys.getLong(1);
      }
    }
  }

  private void insertAuthToken(
      Connection connection,
      long accountId,
      String tokenHash,
      Instant revokedAt,
      Instant lastUsedAt)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            insert into account_auth_tokens (
                account_id, token_hash, revoked_at, last_used_at, created_at, updated_at
            ) values (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """)) {
      statement.setLong(1, accountId);
      statement.setString(2, tokenHash);
      statement.setObject(3, revokedAt);
      statement.setObject(4, lastUsedAt);
      statement.executeUpdate();
    }
  }

  private boolean tableExists(Connection connection, String tableName) throws Exception {
    try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName, null)) {
      return tables.next();
    }
  }
}
