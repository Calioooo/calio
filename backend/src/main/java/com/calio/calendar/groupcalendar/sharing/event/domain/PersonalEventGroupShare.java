package com.calio.calendar.groupcalendar.sharing.event.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.groupspace.domain.GroupSpace;
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
        name = "personal_event_group_shares",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_personal_event_group_share_event_group",
                columnNames = {"event_id", "group_space_id"}
        )
)
public class PersonalEventGroupShare extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_space_id", nullable = false)
    private GroupSpace groupSpace;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous = true;

    @Column(name = "public_share_id", nullable = false, unique = true, updatable = false)
    private UUID publicShareId;

    protected PersonalEventGroupShare() {
    }

    public PersonalEventGroupShare(Event event, GroupSpace groupSpace) {
        this.event = event;
        this.groupSpace = groupSpace;
        this.publicShareId = UUID.randomUUID();
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
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

    public String resolvePublicTitle(String anonymousTitle) {
        return anonymous ? anonymousTitle : event.getTitle();
    }

    public String resolvePublicDescription() {
        return anonymous ? null : event.getDescription();
    }

    public java.time.Instant resolveStartAt() {
        return event.getStartAt();
    }

    public java.time.Instant resolveEndAt() {
        return event.getEndAt();
    }

    public boolean resolveAllDay() {
        return event.isAllDay();
    }
}
