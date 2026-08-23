package com.calio.calendar.groupcalendar.sharing.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
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
        name = "personal_recurrence_group_share_occurrence_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_personal_recurrence_group_share_occurrence_override",
                columnNames = {"share_id", "origin_start_at"}
        )
)
public class PersonalRecurrenceGroupShareOccurrenceOverride extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_id", nullable = false)
    private PersonalRecurrenceGroupShare share;

    @Column(name = "origin_start_at", nullable = false)
    private Instant originStartAt;

    @Column(name = "override_title")
    private String overrideTitle;

    @Column(name = "override_start_at")
    private Instant overrideStartAt;

    @Column(name = "override_end_at")
    private Instant overrideEndAt;

    @Column(name = "override_all_day")
    private Boolean overrideAllDay;

    protected PersonalRecurrenceGroupShareOccurrenceOverride() {
    }

    public PersonalRecurrenceGroupShareOccurrenceOverride(
            PersonalRecurrenceGroupShare share,
            Instant originStartAt
    ) {
        this.share = share;
        this.originStartAt = originStartAt;
    }

    public void updateRepresentation(
            String overrideTitle,
            Instant overrideStartAt,
            Instant overrideEndAt,
            Boolean overrideAllDay
    ) {
        validateEffectiveSchedule(overrideStartAt, overrideEndAt, overrideAllDay);
        this.overrideTitle = overrideTitle;
        this.overrideStartAt = overrideStartAt;
        this.overrideEndAt = overrideEndAt;
        this.overrideAllDay = overrideAllDay;
    }

    private void validateEffectiveSchedule(
            Instant overrideStartAt,
            Instant overrideEndAt,
            Boolean overrideAllDay
    ) {
        RecurrenceEvent recurrenceEvent = share.getRecurrenceEvent();
        boolean allDay = overrideAllDay != null
                ? overrideAllDay
                : share.resolveAllDay(recurrenceEvent.isAllDay());
        CanonicalSchedule.event(
                overrideStartAt != null
                        ? overrideStartAt
                        : share.resolveStartAt(recurrenceEvent.getFirstOccurrenceStartAt()),
                overrideEndAt != null
                        ? overrideEndAt
                        : share.resolveEndAt(recurrenceEvent.getFirstOccurrenceEndAt()),
                allDay,
                allDay ? null : recurrenceEvent.getTimeZone()
        );
    }

    public Long getId() {
        return id;
    }

    public PersonalRecurrenceGroupShare getShare() {
        return share;
    }

    public Instant getOriginStartAt() {
        return originStartAt;
    }

    public String getOverrideTitle() {
        return overrideTitle;
    }

    public Instant getOverrideStartAt() {
        return overrideStartAt;
    }

    public Instant getOverrideEndAt() {
        return overrideEndAt;
    }

    public Boolean getOverrideAllDay() {
        return overrideAllDay;
    }

    public String resolvePublicTitle(String sourceTitle, String anonymousTitle) {
        return overrideTitle != null
                ? overrideTitle
                : share.resolvePublicTitle(sourceTitle, anonymousTitle);
    }

    public Instant resolveStartAt(Instant sourceStartAt) {
        return overrideStartAt != null ? overrideStartAt : share.resolveStartAt(sourceStartAt);
    }

    public Instant resolveEndAt(Instant sourceEndAt) {
        return overrideEndAt != null ? overrideEndAt : share.resolveEndAt(sourceEndAt);
    }

    public boolean resolveAllDay(boolean sourceAllDay) {
        return overrideAllDay != null ? overrideAllDay : share.resolveAllDay(sourceAllDay);
    }
}
