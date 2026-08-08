package com.calio.calendar.holiday.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NationalHolidayMigrationTest {

    @Test
    @DisplayName("national_holidays는 Flyway schema에서 holidayDate/title 조합의 중복을 거부한다")
    void givenDuplicateDateAndTitle_whenInsertThenUniqueConstraintRejectsDuplicateRow() throws Exception {
        // given
        String url = "jdbc:h2:mem:national-holiday-migration;MODE=MySQL;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(insertHolidaySql());

            // when, then
            assertThatThrownBy(() -> statement.executeUpdate(insertHolidaySql()))
                    .isInstanceOf(SQLException.class);
        }
    }

    private String insertHolidaySql() {
        return """
                INSERT INTO national_holidays (
                    holiday_date, holiday_title, created_at, updated_at
                ) VALUES (
                    '2026-10-03', '개천절', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                )
                """;
    }
}
