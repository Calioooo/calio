package com.calio.calendar.groupcalendar.sharing.event.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.domain.CanonicalSchedule;
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
import java.time.Instant;

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

    protected PersonalEventGroupShare() {
    }

    public PersonalEventGroupShare(Event event, GroupSpace groupSpace) {
        this.event = event;
        this.groupSpace = groupSpace;
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
        boolean allDay = overrideAllDay != null ? overrideAllDay : event.isAllDay();
        CanonicalSchedule.event(
                overrideStartAt != null ? overrideStartAt : event.getStartAt(),
                overrideEndAt != null ? overrideEndAt : event.getEndAt(),
                allDay,
                allDay ? null : event.getTimeZone()
        );
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

    public String resolvePublicTitle(String anonymousTitle) {
        if (overrideTitle != null) {
            return overrideTitle;
        }
        return showOriginalDetails ? event.getTitle() : anonymousTitle;
    }

    public String resolvePublicDescription() {
        return showOriginalDetails ? event.getDescription() : null;
    }

    public Instant resolveStartAt() {
        return overrideStartAt != null ? overrideStartAt : event.getStartAt();
    }

    public Instant resolveEndAt() {
        return overrideEndAt != null ? overrideEndAt : event.getEndAt();
    }

    public boolean resolveAllDay() {
        return overrideAllDay != null ? overrideAllDay : event.isAllDay();
    }
}
