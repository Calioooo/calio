package com.calio.calendar.groupcalendar.sharing.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "personal_recurrence_group_shares",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_personal_recurrence_group_share_recurrence_group",
                columnNames = {"recurrence_event_id", "group_space_id"}
        )
)
public class PersonalRecurrenceGroupShare extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurrence_event_id", nullable = false)
    private RecurrenceEvent recurrenceEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_space_id", nullable = false)
    private GroupSpace groupSpace;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_scope", nullable = false)
    private PersonalRecurrenceGroupShareScope shareScope;

    @Column(name = "show_original_details", nullable = false)
    private boolean showOriginalDetails;

    @Column(name = "override_title")
    private String overrideTitle;

    @Column(name = "override_start_at")
    private Instant overrideStartAt;

    @Column(name = "override_end_at")
    private Instant overrideEndAt;

    @Column(name = "override_all_day")
    private Boolean overrideAllDay;

    protected PersonalRecurrenceGroupShare() {
    }

    public PersonalRecurrenceGroupShare(
            RecurrenceEvent recurrenceEvent,
            GroupSpace groupSpace,
            PersonalRecurrenceGroupShareScope shareScope
    ) {
        this.recurrenceEvent = recurrenceEvent;
        this.groupSpace = groupSpace;
        this.shareScope = shareScope;
    }

    public void updateRepresentation(
            boolean showOriginalDetails,
            String overrideTitle,
            Instant overrideStartAt,
            Instant overrideEndAt,
            Boolean overrideAllDay
    ) {
        validateEffectiveSchedule(overrideStartAt, overrideEndAt, overrideAllDay);
        this.showOriginalDetails = showOriginalDetails;
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
        boolean allDay = overrideAllDay != null ? overrideAllDay : recurrenceEvent.isAllDay();
        CanonicalSchedule.event(
                overrideStartAt != null ? overrideStartAt : recurrenceEvent.getFirstOccurrenceStartAt(),
                overrideEndAt != null ? overrideEndAt : recurrenceEvent.getFirstOccurrenceEndAt(),
                allDay,
                allDay ? null : recurrenceEvent.getTimeZone()
        );
    }

    public Long getId() {
        return id;
    }

    public RecurrenceEvent getRecurrenceEvent() {
        return recurrenceEvent;
    }

    public GroupSpace getGroupSpace() {
        return groupSpace;
    }

    public PersonalRecurrenceGroupShareScope getShareScope() {
        return shareScope;
    }

    public boolean isShowOriginalDetails() {
        return showOriginalDetails;
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
        if (overrideTitle != null) {
            return overrideTitle;
        }
        return showOriginalDetails ? sourceTitle : anonymousTitle;
    }

    public Instant resolveStartAt(Instant sourceStartAt) {
        return overrideStartAt != null ? overrideStartAt : sourceStartAt;
    }

    public Instant resolveEndAt(Instant sourceEndAt) {
        return overrideEndAt != null ? overrideEndAt : sourceEndAt;
    }

    public boolean resolveAllDay(boolean sourceAllDay) {
        return overrideAllDay != null ? overrideAllDay : sourceAllDay;
    }
}
