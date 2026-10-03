package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "recurrence_events")
public class RecurrenceEvent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Embedded
  @AttributeOverride(
      name = "value",
      column =
          @Column(
              name = "recurrence_title",
              nullable = false,
              length = RecurrenceEventTitle.MAX_LENGTH))
  private RecurrenceEventTitle title;

  @Column(name = "recurrence_description")
  private String description;

  @Embedded private RecurrenceSchedule schedule;

  @Column(name = "recurrence_rule", nullable = false, columnDefinition = "TEXT")
  @Convert(converter = RecurrenceRuleJsonConverter.class)
  private List<String> recurrenceRules = List.of();

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "tag_id", nullable = false)
  private Long tagId;

  protected RecurrenceEvent() {}

  public RecurrenceEvent(
      String title,
      String description,
      RecurrenceSchedule schedule,
      List<String> recurrenceRules,
      Long tagId,
      Long accountId) {
    this.title = new RecurrenceEventTitle(title);
    this.description = description;
    replaceSchedule(schedule, recurrenceRules);
    this.tagId = tagId;
    this.accountId = accountId;
  }

  public void update(
      String title,
      String description,
      RecurrenceSchedule schedule,
      List<String> recurrenceRules,
      Long tagId) {
    this.title = new RecurrenceEventTitle(title);
    this.description = description;
    replaceSchedule(schedule, recurrenceRules);
    this.tagId = tagId;
  }

  public void updateProviderContent(
      String title, String description, RecurrenceSchedule schedule, List<String> recurrenceRules) {
    this.title = new RecurrenceEventTitle(title);
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
    return title.value();
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

  public Long getTagId() {
    return tagId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public RecurrenceEventOverride changeOccurrence(
      RecurrenceEventOverride existing,
      Instant originStartAt,
      boolean generatedOrigin,
      String title,
      String description,
      CanonicalSchedule schedule) {
    requireEligibleOccurrence(existing, originStartAt, generatedOrigin);
    if (existing == null) {
      return RecurrenceEventOverride.active(this, originStartAt, title, description, schedule);
    }
    existing.activate(title, description, schedule);
    return existing;
  }

  public RecurrenceEventOverride excludeOccurrence(
      RecurrenceEventOverride existing,
      Instant originStartAt,
      boolean generatedOrigin,
      Instant deletedAt) {
    requireEligibleOccurrence(existing, originStartAt, generatedOrigin);
    if (existing == null) {
      return RecurrenceEventOverride.deleted(this, originStartAt, deletedAt);
    }
    existing.markDeleted(deletedAt);
    return existing;
  }

  private void requireEligibleOccurrence(
      RecurrenceEventOverride existing, Instant originStartAt, boolean generatedOrigin) {
    if (existing == null && !generatedOrigin) {
      throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    }
    if (existing != null
        && (!Objects.equals(id, existing.getRecurrenceId())
            || !Objects.equals(originStartAt, existing.getOriginStartAt()))) {
      throw new IllegalArgumentException("Override belongs to a different recurrence event.");
    }
  }
}
