package com.calio.calendar.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "events")
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(name = "important_event", nullable = false, columnDefinition = "boolean default false")
    private boolean importantEvent = false;

    @Column(name = "recurrence_id")
    private Long recurrenceId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Event() {
    }

    public Event(String title, String description, Instant startAt, Instant endAt) {
        this(title, description, startAt, endAt, null);
    }

    public Event(String title, String description, Instant startAt, Instant endAt, Long recurrenceId) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.recurrenceId = recurrenceId;
    }

    public void replace(String title, String description, Instant startAt, Instant endAt) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void updateOccurrence(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean importantEvent
    ) {
        replace(title, description, startAt, endAt);
        changeImportantEvent(importantEvent);
    }

    public void changeImportantEvent(boolean importantEvent) {
        this.importantEvent = importantEvent;
    }

    public void softDelete(Instant deletedAt) {
        if (this.deletedAt != null) {
            return;
        }

        this.deletedAt = deletedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public boolean importantEvent() {
        return importantEvent;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Optional<Long> getRecurrenceId() {
        return Optional.ofNullable(recurrenceId);
    }

    public boolean isRecurrenceOccurrence() {
        return recurrenceId != null;
    }
}
