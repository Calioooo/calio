package com.calio.calendar.groupcalendar.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.domain.CanonicalSchedule;
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
        name = "group_calendar_recurrence_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_group_recurrence_override",
                columnNames = {"recurrence_event_id", "origin_start_at"}
        )
)
public class GroupCalendarRecurrenceOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurrence_event_id", nullable = false)
    private GroupCalendarRecurrenceEvent recurrenceEvent;

    @Column(name = "origin_start_at", nullable = false)
    private Instant originStartAt;

    @Column(name = "override_title")
    private String title;

    @Column(name = "override_description")
    private String description;

    @Column(name = "override_start_at")
    private Instant startAt;

    @Column(name = "override_end_at")
    private Instant endAt;

    @Column(name = "override_all_day")
    private Boolean allDay;

    @Column(name = "override_time_zone")
    private String timeZone;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected GroupCalendarRecurrenceOverride() {
    }

    private GroupCalendarRecurrenceOverride(GroupCalendarRecurrenceEvent recurrenceEvent, Instant originStartAt) {
        this.recurrenceEvent = recurrenceEvent;
        this.originStartAt = originStartAt;
    }

    public static GroupCalendarRecurrenceOverride active(
            GroupCalendarRecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            String title,
            String description,
            CanonicalSchedule schedule
    ) {
        GroupCalendarRecurrenceOverride override = new GroupCalendarRecurrenceOverride(
                recurrenceEvent,
                originStartAt
        );
        override.activate(title, description, schedule);
        return override;
    }

    public static GroupCalendarRecurrenceOverride deleted(
            GroupCalendarRecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            Instant deletedAt
    ) {
        GroupCalendarRecurrenceOverride override = new GroupCalendarRecurrenceOverride(
                recurrenceEvent,
                originStartAt
        );
        override.markDeleted(deletedAt);
        return override;
    }

    public void activate(String title, String description, CanonicalSchedule schedule) {
        this.title = title;
        this.description = description;
        this.startAt = schedule.startAt();
        this.endAt = schedule.endAt();
        this.allDay = schedule.allDay();
        this.timeZone = schedule.timeZone();
        this.deletedAt = null;
    }

    public void markDeleted(Instant deletedAt) {
        this.title = null;
        this.description = null;
        this.startAt = null;
        this.endAt = null;
        this.allDay = null;
        this.timeZone = null;
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public GroupCalendarRecurrenceEvent getRecurrenceEvent() {
        return recurrenceEvent;
    }

    public Instant getOriginStartAt() {
        return originStartAt;
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

    public boolean isAllDay() {
        return Boolean.TRUE.equals(allDay);
    }

    public String getTimeZone() {
        return timeZone;
    }
}
