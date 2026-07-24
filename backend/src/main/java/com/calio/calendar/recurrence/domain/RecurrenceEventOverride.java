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

    @Column(name = "override_title")
    private String overrideTitle;

    @Column(name = "override_description")
    private String overrideDescription;

    @Column(name = "override_start_at")
    private Instant overrideStartAt;

    @Column(name = "override_end_at")
    private Instant overrideEndAt;

    @Column(name = "override_all_day")
    private Boolean overrideAllDay;

    @Column(name = "override_time_zone")
    private String overrideTimeZone;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected RecurrenceEventOverride() {
    }

    private RecurrenceEventOverride(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        this.recurrenceEvent = recurrenceEvent;
        this.originStartAt = originStartAt;
    }

    public static RecurrenceEventOverride active(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            String title,
            String description,
            Instant startAt,
            Instant endAt
    ) {
        RecurrenceEventOverride override = new RecurrenceEventOverride(recurrenceEvent, originStartAt);
        override.activate(title, description, startAt, endAt);
        return override;
    }

    public static RecurrenceEventOverride deleted(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            Instant deletedAt
    ) {
        RecurrenceEventOverride override = new RecurrenceEventOverride(recurrenceEvent, originStartAt);
        override.markDeleted(deletedAt);
        return override;
    }

    public void activate(String title, String description, Instant startAt, Instant endAt) {
        this.overrideTitle = title;
        this.overrideDescription = description;
        this.overrideStartAt = startAt;
        this.overrideEndAt = endAt;
        this.overrideAllDay = recurrenceEvent.isAllDay();
        this.overrideTimeZone = recurrenceEvent.getTimeZone();
        this.deletedAt = null;
    }

    public void markDeleted(Instant deletedAt) {
        this.overrideTitle = null;
        this.overrideDescription = null;
        this.overrideStartAt = null;
        this.overrideEndAt = null;
        this.overrideAllDay = null;
        this.overrideTimeZone = null;
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
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

    public String getOverrideTitle() {
        return overrideTitle;
    }

    public String getOverrideDescription() {
        return overrideDescription;
    }

    public Instant getOverrideStartAt() {
        return overrideStartAt;
    }

    public Instant getOverrideEndAt() {
        return overrideEndAt;
    }

    public boolean isOverrideAllDay() {
        return Boolean.TRUE.equals(overrideAllDay);
    }

    public String getOverrideTimeZone() {
        return overrideTimeZone;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
