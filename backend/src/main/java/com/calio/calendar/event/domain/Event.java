package com.calio.calendar.event.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
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
import java.util.Optional;

@Entity
@Table(name = "events")
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "important_event", nullable = false, columnDefinition = "boolean default false")
    private boolean importantEvent = false;

    @Column(name = "recurrence_id")
    private Long recurrenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    protected Event() {
    }

    public Event(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            Long recurrenceId,
            Tag tag,
            Account account
    ) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
        this.recurrenceId = recurrenceId;
        this.tag = tag;
        this.account = account;
    }

    public void replace(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay
    ) {
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
    }

    public void replace(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            Tag tag
    ) {
        replace(title, description, startAt, endAt, allDay);
        changeTag(tag);
    }

    public void changeImportantEvent(boolean importantEvent) {
        this.importantEvent = importantEvent;
    }

    public void changeTag(Tag tag) {
        this.tag = tag;
    }

    public Long getId() {
        return id;
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
        return allDay;
    }

    public boolean importantEvent() {
        return importantEvent;
    }

    public Optional<Long> getRecurrenceId() {
        return Optional.ofNullable(recurrenceId);
    }

    public boolean isRecurrenceOccurrence() {
        return recurrenceId != null;
    }

    public Tag getTag() {
        return tag;
    }

    public Account getAccount() {
        return account;
    }
}
