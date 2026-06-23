package com.calio.calendar.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "recurrence_event_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recurrence_event_overrides_event_id",
                columnNames = "event_id"
        )
)
public class RecurrenceEventOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long overrideId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    protected RecurrenceEventOverride() {
    }

    public RecurrenceEventOverride(Long eventId) {
        this.eventId = eventId;
    }

    public Long getOverrideId() {
        return overrideId;
    }

    public Long getEventId() {
        return eventId;
    }
}
