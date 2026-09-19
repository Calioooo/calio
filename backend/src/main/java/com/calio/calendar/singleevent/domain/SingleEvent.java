package com.calio.calendar.singleevent.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "events")
public class SingleEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private SingleEventTitle title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Embedded
    private SingleEventSchedule schedule;

    @Column(name = "important_event", nullable = false, columnDefinition = "boolean default false")
    private boolean importantEvent = false;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    protected SingleEvent() {
    }

    public SingleEvent(
            SingleEventTitle title,
            String description,
            SingleEventSchedule schedule,
            Long tagId,
            Long accountId
    ) {
        this.title = title;
        this.description = description;
        this.schedule = schedule;
        this.tagId = tagId;
        this.accountId = accountId;
    }

    public SingleEvent(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone,
            Long tagId,
            Long accountId
    ) {
        this(new SingleEventTitle(title), description, new SingleEventSchedule(startAt, endAt, allDay, timeZone),
                tagId, accountId);
    }

    public void replace(
            SingleEventTitle title,
            String description,
            SingleEventSchedule schedule
    ) {
        this.title = title;
        this.description = description;
        this.schedule = schedule;
    }

    public void replace(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone
    ) {
        replace(new SingleEventTitle(title), description, new SingleEventSchedule(startAt, endAt, allDay, timeZone));
    }

    public void replace(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone,
            Long tagId
    ) {
        replace(title, description, startAt, endAt, allDay, timeZone);
        changeTag(tagId);
    }

    public void changeImportantEvent(boolean importantEvent) {
        this.importantEvent = importantEvent;
    }

    public void changeTag(Long tagId) {
        this.tagId = tagId;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title.value();
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartAt() {
        return schedule.startAt();
    }

    public Instant getEndAt() {
        return schedule.endAt();
    }

    public boolean isAllDay() {
        return schedule.allDay();
    }

    public String getTimeZone() {
        return schedule.timeZone();
    }

    public boolean importantEvent() {
        return importantEvent;
    }

    public Long getTagId() {
        return tagId;
    }

    public Long getAccountId() {
        return accountId;
    }
}
