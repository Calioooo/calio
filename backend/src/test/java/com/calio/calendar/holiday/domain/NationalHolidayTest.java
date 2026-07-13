package com.calio.calendar.holiday.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NationalHolidayTest {

    @Test
    @DisplayName("NationalHoliday는 date/title 조합 unique constraint를 national_holidays에 선언한다")
    void givenNationalHolidayEntity_whenReadTableAnnotation_thenHasDateTitleUniqueConstraint() {
        // when
        Table table = NationalHoliday.class.getAnnotation(Table.class);

        // then
        assertThat(table.name()).isEqualTo("national_holidays");
        assertThat(table.uniqueConstraints()).singleElement()
                .satisfies(uniqueConstraint -> {
                    assertThat(uniqueConstraint.name()).isEqualTo("uk_national_holiday_date_title");
                    assertThat(uniqueConstraint.columnNames()).containsExactly("holiday_date", "holiday_title");
                });
    }

    @Test
    @DisplayName("NationalHoliday는 provider의 dateKind, seq, isHoliday를 저장 필드로 갖지 않는다")
    void givenNationalHolidayEntity_whenReadDeclaredFields_thenDoesNotPersistProviderMetadata() {
        // when
        String[] fieldNames = Arrays.stream(NationalHoliday.class.getDeclaredFields())
                .map(Field::getName)
                .toArray(String[]::new);

        // then
        assertThat(fieldNames)
                .contains("nationalHolidayId", "holidayDate", "holidayTitle")
                .doesNotContain("dateKind", "seq", "isHoliday");
    }

    @Test
    @DisplayName("NationalHoliday는 holidayDate와 holidayTitle을 canonical 저장 값으로 가진다")
    void givenHolidayDateAndTitle_whenCreateNationalHoliday_thenStoresCanonicalFields() {
        // given
        LocalDate holidayDate = LocalDate.parse("2026-10-03");

        // when
        NationalHoliday nationalHoliday = new NationalHoliday(holidayDate, "개천절");

        // then
        assertThat(nationalHoliday.getHolidayDate()).isEqualTo(holidayDate);
        assertThat(nationalHoliday.getHolidayTitle()).isEqualTo("개천절");
    }
}
