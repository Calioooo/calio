package com.calio.calendar.groupcalendar.sharing.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
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
}
