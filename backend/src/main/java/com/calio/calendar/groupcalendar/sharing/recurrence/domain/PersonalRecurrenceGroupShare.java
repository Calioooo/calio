package com.calio.calendar.groupcalendar.sharing.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.groupspace.domain.GroupSpace;
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
import java.util.UUID;

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

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous = true;

    @Column(name = "public_share_id", nullable = false, unique = true, updatable = false)
    private UUID publicShareId;

    protected PersonalRecurrenceGroupShare() {
    }

    public PersonalRecurrenceGroupShare(
            RecurrenceEvent recurrenceEvent,
            GroupSpace groupSpace
    ) {
        this.recurrenceEvent = recurrenceEvent;
        this.groupSpace = groupSpace;
        this.publicShareId = UUID.randomUUID();
    }

    @Deprecated(forRemoval = true)
    public PersonalRecurrenceGroupShare(
            RecurrenceEvent recurrenceEvent,
            GroupSpace groupSpace,
            PersonalRecurrenceGroupShareScope ignoredShareScope
    ) {
        this(recurrenceEvent, groupSpace);
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

    public boolean isAnonymous() {
        return anonymous;
    }

    public UUID getPublicShareId() {
        return publicShareId;
    }

    public String resolvePublicTitle(String sourceTitle, String anonymousTitle) {
        return anonymous ? anonymousTitle : sourceTitle;
    }

    @Deprecated(forRemoval = true)
    public void updateRepresentation(
            boolean ignoredShowOriginalDetails,
            String ignoredTitle,
            java.time.Instant ignoredStartAt,
            java.time.Instant ignoredEndAt,
            Boolean ignoredAllDay
    ) {
        // Share-specific representation overrides are intentionally no longer persisted.
    }

    public java.time.Instant resolveStartAt(java.time.Instant sourceStartAt) {
        return sourceStartAt;
    }

    public java.time.Instant resolveEndAt(java.time.Instant sourceEndAt) {
        return sourceEndAt;
    }

    public boolean resolveAllDay(boolean sourceAllDay) {
        return sourceAllDay;
    }
}
