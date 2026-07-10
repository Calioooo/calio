package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.Tag;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateEventRequest(
        @NotBlank(message = "이벤트 제목은 공백일 수 없습니다.") String title,
        String description,
        @NotNull(message = "이벤트 시작 시각은 필수입니다.") Instant startAt,
        @NotNull(message = "이벤트 종료 시각은 필수입니다.") Instant endAt,
        Long tagId
) {

    public Event toEntity(Tag tag) {
        return new Event(title, description, startAt, endAt, null, tag);
    }

    public Event toEntity(Tag tag, Account account) {
        return new Event(title, description, startAt, endAt, null, tag, account);
    }
}
