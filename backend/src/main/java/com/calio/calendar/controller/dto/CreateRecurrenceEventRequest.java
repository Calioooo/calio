package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
import com.calio.calendar.repository.entity.Tag;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateRecurrenceEventRequest(
        @NotBlank(message = "반복 일정 제목은 공백일 수 없습니다.") String recurrenceTitle,
        String recurrenceDescription,
        @NotNull(message = "반복 일정 시작 날짜는 필수입니다.") LocalDate recurrenceStartDate,
        @NotNull(message = "반복 일정 종료 날짜는 필수입니다.") LocalDate recurrenceEndDate,
        @NotNull(message = "반복 일정 시작 시각은 필수입니다.") LocalTime recurrenceStartTime,
        @NotNull(message = "반복 일정 종료 시각은 필수입니다.") LocalTime recurrenceEndTime,
        @NotNull(message = "반복 일정 주기는 필수입니다.") RecurrenceFrequency recurrenceFrequency,
        Long tagId
) {
    public RecurrenceEvent toEntity(Tag tag, Account account) {
        return new RecurrenceEvent(
                recurrenceTitle,
                recurrenceDescription,
                recurrenceStartDate,
                recurrenceEndDate,
                recurrenceStartTime,
                recurrenceEndTime,
                recurrenceFrequency,
                tag,
                account
        );
    }
}
