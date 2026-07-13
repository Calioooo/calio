package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "recurrence_event_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recurrence_event_overrides_recurrence_origin",
                columnNames = {"recurrence_id", "origin_start_at"}
        )
)
public class RecurrenceEventOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long overrideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurrence_id", nullable = false)
    private RecurrenceEvent recurrenceEvent;

    @Column(name = "origin_start_at", nullable = false)
    private Instant originStartAt;

    @Column(name = "override_start_at")
    private Instant overrideStartAt;

    @Column(name = "override_end_at")
    private Instant overrideEndAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected RecurrenceEventOverride() {
    }

    private RecurrenceEventOverride(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            Instant overrideStartAt,
            Instant overrideEndAt,
            Instant deletedAt
    ) {
        this.recurrenceEvent = recurrenceEvent;
        this.originStartAt = originStartAt;
        this.overrideStartAt = overrideStartAt;
        this.overrideEndAt = overrideEndAt;
        this.deletedAt = deletedAt;
    }

    public static RecurrenceEventOverride modified(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            Instant overrideStartAt,
            Instant overrideEndAt
    ) {
        return new RecurrenceEventOverride(recurrenceEvent, originStartAt, overrideStartAt, overrideEndAt, null);
    }

    public static RecurrenceEventOverride deleted(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            Instant deletedAt
    ) {
        return new RecurrenceEventOverride(recurrenceEvent, originStartAt, null, null, deletedAt);
    }

    public void changeModifiedTime(Instant overrideStartAt, Instant overrideEndAt) {
        this.overrideStartAt = overrideStartAt;
        this.overrideEndAt = overrideEndAt;
        this.deletedAt = null;
    }

    public void markDeleted(Instant deletedAt) {
        this.overrideStartAt = null;
        this.overrideEndAt = null;
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getOverrideId() {
        return overrideId;
    }

    public RecurrenceEvent getRecurrenceEvent() {
        return recurrenceEvent;
    }

    public Long getRecurrenceId() {
        return recurrenceEvent.getId();
    }

    public Instant getOriginStartAt() {
        return originStartAt;
    }

    public Instant getOverrideStartAt() {
        return overrideStartAt;
    }

    public Instant getOverrideEndAt() {
        return overrideEndAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
