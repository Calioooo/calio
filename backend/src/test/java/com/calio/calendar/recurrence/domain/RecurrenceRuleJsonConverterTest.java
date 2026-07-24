package com.calio.calendar.recurrence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurrenceRuleJsonConverterTest {

    private final RecurrenceRuleJsonConverter converter = new RecurrenceRuleJsonConverter();

    @Test
    @DisplayName("반복 규칙 목록을 JSON으로 직렬화하고 동일한 목록으로 복원한다")
    void givenRecurrenceRules_whenConvertRoundTrip_thenPreservesValues() {
        // given
        List<String> recurrenceRules = List.of(
                "RRULE:FREQ=WEEKLY;BYDAY=TH",
                "EXDATE:20260730T090000Z",
                "X-NOTE:quote=\"value\";path=C:\\calendar"
        );

        // when
        String json = converter.convertToDatabaseColumn(recurrenceRules);
        List<String> restored = converter.convertToEntityAttribute(json);

        // then
        assertThat(restored).containsExactlyElementsOf(recurrenceRules);
    }

    @Test
    @DisplayName("유효하지 않은 JSON은 명시적인 변환 오류로 처리한다")
    void givenInvalidJson_whenConvertToEntityAttribute_thenThrowsException() {
        // given
        String invalidJson = "[\"RRULE:FREQ=DAILY\"";

        // when, then
        assertThatThrownBy(() -> converter.convertToEntityAttribute(invalidJson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid recurrence_rule JSON.");
    }
}
