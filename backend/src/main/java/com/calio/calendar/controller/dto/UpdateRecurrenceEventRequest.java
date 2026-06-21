package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.RecurrenceFrequency;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;

public class UpdateRecurrenceEventRequest {

    private String title;
    private String description;
    private Instant startAt;
    private Instant endAt;
    private RecurrenceFrequency recurrenceFrequency;

    public UpdateRecurrenceEventRequest() {
    }

    public UpdateRecurrenceEventRequest(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            RecurrenceFrequency recurrenceFrequency
    ) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.recurrenceFrequency = recurrenceFrequency;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("Unsupported field: " + fieldName);
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Instant startAt() {
        return startAt;
    }

    public Instant endAt() {
        return endAt;
    }

    public RecurrenceFrequency recurrenceFrequency() {
        return recurrenceFrequency;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public void setRecurrenceFrequency(RecurrenceFrequency recurrenceFrequency) {
        this.recurrenceFrequency = recurrenceFrequency;
    }
}
