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

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "origin_start_at", nullable = false)
    private Instant originStartAt;

    @Column(name = "origin_end_at", nullable = false)
    private Instant originEndAt;

    @Column(name = "override_start_at")
    private Instant overrideStartAt;

    @Column(name = "override_end_at")
    private Instant overrideEndAt;

    protected RecurrenceEventOverride() {
    }

    public RecurrenceEventOverride(
            Long recurrenceId,
            boolean deleted,
            Instant originStartAt,
            Instant originEndAt,
            Instant overrideStartAt,
            Instant overrideEndAt
    ) {
        this.recurrenceId = recurrenceId;
        this.deleted = deleted;
        this.originStartAt = originStartAt;
        this.originEndAt = originEndAt;
        this.overrideStartAt = overrideStartAt;
        this.overrideEndAt = overrideEndAt;
    }

    public void replaceModifiedTime(Instant overrideStartAt, Instant overrideEndAt) {
        this.deleted = false;
        this.overrideStartAt = overrideStartAt;
        this.overrideEndAt = overrideEndAt;
    }

    public Long getOverrideId() {
        return overrideId;
    }

    public Long getRecurrenceId() {
        return recurrenceId;
    }

    public boolean isDeleted() {
        return deleted;
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
