package com.calio.calendar.recurrence.domain;

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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "recurrence_events")
public class RecurrenceEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recurrenceTitle;

    @Column
    private String recurrenceDescription;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "time_zone")
    private String timeZone;

    @Column(name = "recurrence_start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "recurrence_end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "recurrence_start_time")
    private LocalTime startTime;

    @Column(name = "recurrence_end_time")
    private LocalTime endTime;

    @Column(name = "recurrence_lines", nullable = false, columnDefinition = "TEXT")
    private String recurrenceLines;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    protected RecurrenceEvent() {
    }

    public RecurrenceEvent(
            String title,
            String description,
            RecurrenceSchedule schedule,
            List<String> recurrenceLines,
            Tag tag,
            Account account
    ) {
        this.recurrenceTitle = title;
        this.recurrenceDescription = description;
        replaceSchedule(schedule, recurrenceLines);
        this.tag = tag;
        this.account = account;
    }

    public void update(
            String title,
            String description,
            RecurrenceSchedule schedule,
            List<String> recurrenceLines,
            Tag tag
    ) {
        this.recurrenceTitle = title;
        this.recurrenceDescription = description;
        replaceSchedule(schedule, recurrenceLines);
        this.tag = tag;
    }

    private void replaceSchedule(RecurrenceSchedule schedule, List<String> lines) {
        this.allDay = schedule.allDay();
        this.startAt = schedule.startAt();
        this.endAt = schedule.endAt();
        this.timeZone = schedule.timeZone();
        this.startDate = schedule.startDate();
        this.endDate = schedule.endDate();
        this.startTime = schedule.startTime();
        this.endTime = schedule.endTime();
        this.recurrenceLines = RecurrenceLinesJson.encode(lines);
    }

    public Long getId() {
        return id;
    }

    public String getRecurrenceTitle() {
        return recurrenceTitle;
    }

    public String getRecurrenceDescription() {
        return recurrenceDescription;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public List<String> getRecurrenceLines() {
        return RecurrenceLinesJson.decode(recurrenceLines);
    }

    public Tag getTag() {
        return tag;
    }

    public Account getAccount() {
        return account;
    }
}
