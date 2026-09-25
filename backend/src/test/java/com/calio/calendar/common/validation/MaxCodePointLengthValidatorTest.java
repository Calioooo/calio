package com.calio.calendar.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.singleevent.controller.dto.CreateSingleEventRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MaxCodePointLengthValidatorTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("단일 일정 요청은 80개의 보조 문자를 제목으로 허용한다")
  void givenTitleWithEightyCodePoints_whenValidateSingleEventRequest_thenAccepts() {
    CreateSingleEventRequest request =
        new CreateSingleEventRequest(
            "😀".repeat(80),
            null,
            Instant.parse("2026-09-25T00:00:00Z"),
            Instant.parse("2026-09-25T01:00:00Z"),
            false,
            "UTC",
            null);

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  @DisplayName("반복 일정 요청은 81개의 보조 문자를 제목으로 거부한다")
  void givenTitleWithEightyOneCodePoints_whenValidateRecurrenceRequest_thenRejects() {
    CreateRecurrenceEventRequest request =
        new CreateRecurrenceEventRequest(
            "😀".repeat(81),
            null,
            false,
            Instant.parse("2026-09-25T00:00:00Z"),
            Instant.parse("2026-09-25T01:00:00Z"),
            "UTC",
            List.of("RRULE:FREQ=DAILY;COUNT=2"),
            null);

    assertThat(validator.validate(request)).isNotEmpty();
  }
}
