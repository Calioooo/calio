package com.calio.calendar.recurrence.domain;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.domain.CalendarEventTitle;
import com.calio.calendar.tag.domain.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
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
@Table(name = "recurrence_events")
public class RecurrenceEvent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "recurrence_title", nullable = false)
  private String title;

  @Column(name = "recurrence_description")
  private String description;

  @Embedded private RecurrenceSchedule schedule;

  @Column(name = "recurrence_rule", nullable = false, columnDefinition = "TEXT")
  @Convert(converter = RecurrenceRuleJsonConverter.class)
  private List<String> recurrenceRules = List.of();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tag_id", nullable = false)
  private Tag tag;

  protected RecurrenceEvent() {}

  public RecurrenceEvent(
      String title,
      String description,
      RecurrenceSchedule schedule,
      List<String> recurrenceRules,
      Tag tag,
      Account account) {
    this.title = CalendarEventTitle.requireValid(title);
    this.description = description;
    replaceSchedule(schedule, recurrenceRules);
    this.tag = tag;
    this.account = account;
  }

  public void update(
      String title,
      String description,
      RecurrenceSchedule schedule,
      List<String> recurrenceRules,
      Tag tag) {
    this.title = CalendarEventTitle.requireValid(title);
    this.description = description;
    replaceSchedule(schedule, recurrenceRules);
    this.tag = tag;
  }

  public void updateProviderContent(
      String title, String description, RecurrenceSchedule schedule, List<String> recurrenceRules) {
    this.title = CalendarEventTitle.requireValid(title);
    this.description = description;
    replaceSchedule(schedule, recurrenceRules);
  }

  private void replaceSchedule(RecurrenceSchedule schedule, List<String> recurrenceRules) {
    this.schedule = schedule;
    this.recurrenceRules = List.copyOf(recurrenceRules);
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

  public boolean isAllDay() {
    return schedule.allDay();
  }

  public String getTimeZone() {
    return schedule.timeZone();
  }

  public Instant getFirstOccurrenceStartAt() {
    return schedule.firstOccurrenceStartAt();
  }

  public Instant getFirstOccurrenceEndAt() {
    return schedule.firstOccurrenceEndAt();
  }

  public List<String> getRecurrenceRules() {
    return recurrenceRules;
  }

  public Tag getTag() {
    return tag;
  }

  public Account getAccount() {
    return account;
  }
}
