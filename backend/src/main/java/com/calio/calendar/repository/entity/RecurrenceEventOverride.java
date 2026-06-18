package com.calio.calendar.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "recurrence_event_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recurrence_event_overrides_recurrence_origin_start",
                columnNames = {"recurrence_id", "origin_start_at"}
        )
)
public class RecurrenceEventOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long overrideId;

    @Column(name = "recurrence_id", nullable = false)
    private Long recurrenceId;

    @Column(nullable = false)
    private boolean isDeleted;

    @Column(nullable = false)
    private Instant originStartAt;

    @Column(nullable = false)
    private Instant originEndAt;

    @Column
    private Instant overrideStartAt;

    @Column
    private Instant overrideEndAt;

    protected RecurrenceEventOverride() {
    }

    public RecurrenceEventOverride(
            Long recurrenceId,
            Instant originStartAt,
            Instant originEndAt,
            Instant overrideStartAt,
            Instant overrideEndAt
    ) {
        this.recurrenceId = recurrenceId;
        this.isDeleted = false;
        this.originStartAt = originStartAt;
        this.originEndAt = originEndAt;
        this.overrideStartAt = requireOverrideTimestamp(overrideStartAt);
        this.overrideEndAt = requireOverrideTimestamp(overrideEndAt);
    }

    public RecurrenceEventOverride(Long recurrenceId, Instant originStartAt, Instant originEndAt) {
        this.recurrenceId = recurrenceId;
        this.isDeleted = true;
        this.originStartAt = originStartAt;
        this.originEndAt = originEndAt;
    }

    public void replaceModification(
            Instant originEndAt,
            Instant overrideStartAt,
            Instant overrideEndAt
    ) {
        this.isDeleted = false;
        this.originEndAt = originEndAt;
        this.overrideStartAt = requireOverrideTimestamp(overrideStartAt);
        this.overrideEndAt = requireOverrideTimestamp(overrideEndAt);
    }

    private Instant requireOverrideTimestamp(Instant timestamp) {
        if (timestamp != null) {
            return timestamp;
        }

        throw new IllegalArgumentException("Modification override timestamps are required.");
    }

    public Long getOverrideId() {
        return overrideId;
    }

    public Long getRecurrenceId() {
        return recurrenceId;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public Instant getOriginStartAt() {
        return originStartAt;
    }

    public Instant getOriginEndAt() {
        return originEndAt;
    }

    public Instant getOverrideStartAt() {
        return overrideStartAt;
    }

    public Instant getOverrideEndAt() {
        return overrideEndAt;
    }
}
