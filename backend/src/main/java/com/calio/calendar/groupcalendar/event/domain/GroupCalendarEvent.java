package com.calio.calendar.groupcalendar.event.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.tag.domain.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "group_calendar_events")
public class GroupCalendarEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_space_id", nullable = false)
    private GroupSpace groupSpace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private Account createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private boolean allDay;

    private String timeZone;

    protected GroupCalendarEvent() {
    }

    public GroupCalendarEvent(
            GroupSpace groupSpace,
            Account createdBy,
            Tag tag,
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone
    ) {
        this.groupSpace = groupSpace;
        this.createdBy = createdBy;
        replace(title, description, startAt, endAt, allDay, timeZone, tag);
    }

    public void replace(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone,
            Tag tag
    ) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.timeZone = timeZone;
        this.tag = tag;
    }

    public Long getId() { return id; }
    public GroupSpace getGroupSpace() { return groupSpace; }
    public Account getCreatedBy() { return createdBy; }
    public Tag getTag() { return tag; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public boolean isAllDay() { return allDay; }
    public String getTimeZone() { return timeZone; }
}
