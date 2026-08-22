package com.calio.calendar.groupcalendar.recurrence.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.recurrence.domain.RecurrenceRuleJsonConverter;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.tag.domain.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "group_calendar_recurrence_events")
public class GroupCalendarRecurrenceEvent extends BaseEntity {

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

    @Column(name = "recurrence_title", nullable = false)
    private String title;

    @Column(name = "recurrence_description")
    private String description;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "time_zone")
    private String timeZone;

    @Column(name = "first_occurrence_start_at", nullable = false)
    private Instant firstOccurrenceStartAt;

    @Column(name = "first_occurrence_end_at", nullable = false)
    private Instant firstOccurrenceEndAt;

    @Convert(converter = RecurrenceRuleJsonConverter.class)
    @Column(name = "recurrence_rule", nullable = false, columnDefinition = "TEXT")
    private List<String> recurrenceRules;

    protected GroupCalendarRecurrenceEvent() {
    }

    public GroupCalendarRecurrenceEvent(
            GroupSpace groupSpace,
            Account createdBy,
            Tag tag,
            String title,
            String description,
            RecurrenceSchedule schedule,
            List<String> recurrenceRules
    ) {
        this.groupSpace = groupSpace;
        this.createdBy = createdBy;
        update(title, description, tag, schedule, recurrenceRules);
    }

    public void update(
            String title,
            String description,
            Tag tag,
            RecurrenceSchedule schedule,
            List<String> recurrenceRules
    ) {
        this.title = title;
        this.description = description;
        this.tag = tag;
        this.allDay = schedule.allDay();
        this.timeZone = schedule.timeZone();
        this.firstOccurrenceStartAt = schedule.firstOccurrenceStartAt();
        this.firstOccurrenceEndAt = schedule.firstOccurrenceEndAt();
        this.recurrenceRules = List.copyOf(recurrenceRules);
    }

    public Long getId() {
        return id;
    }

    public GroupSpace getGroupSpace() {
        return groupSpace;
    }

    public Account getCreatedBy() {
        return createdBy;
    }

    public Tag getTag() {
        return tag;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Instant getFirstOccurrenceStartAt() {
        return firstOccurrenceStartAt;
    }

    public Instant getFirstOccurrenceEndAt() {
        return firstOccurrenceEndAt;
    }

    public List<String> getRecurrenceRules() {
        return recurrenceRules;
    }

    public RecurrenceSchedule toRecurrenceSchedule() {
        return new RecurrenceSchedule(
                firstOccurrenceStartAt,
                firstOccurrenceEndAt,
                allDay,
                timeZone
        );
    }
}
