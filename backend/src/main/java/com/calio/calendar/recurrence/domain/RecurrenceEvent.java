package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

  @OneToMany(mappedBy = "recurrenceEvent", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<RecurrenceEventOverride> overrides = new ArrayList<>();

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

  public List<RecurrenceEventOverride> getOverrides() {
    return List.copyOf(overrides);
  }

  public Optional<RecurrenceEventOverride> findOverride(Instant originStartAt) {
    Objects.requireNonNull(originStartAt);
    return overrides.stream()
        .filter(override -> override.getOriginStartAt().equals(originStartAt))
        .findFirst();
  }

  public RecurrenceEventOverride changeOccurrence(
      Instant originStartAt,
      boolean generatedOrigin,
      String title,
      String description,
      CanonicalSchedule schedule) {
    RecurrenceEventOverride existing = findOverride(originStartAt).orElse(null);
    requireEligibleOccurrence(existing, generatedOrigin);
    return changeOverride(existing, originStartAt, title, description, schedule);
  }

  public RecurrenceEventOverride updateProviderOccurrence(
      Instant originStartAt, String title, String description, CanonicalSchedule schedule) {
    return changeOverride(
        findOverride(originStartAt).orElse(null), originStartAt, title, description, schedule);
  }

  private RecurrenceEventOverride changeOverride(
      RecurrenceEventOverride existing,
      Instant originStartAt,
      String title,
      String description,
      CanonicalSchedule schedule) {
    if (existing == null) {
      RecurrenceEventOverride created =
          RecurrenceEventOverride.active(this, originStartAt, title, description, schedule);
      overrides.add(created);
      return created;
    }
    existing.activate(title, description, schedule);
    return existing;
  }

  public RecurrenceEventOverride excludeOccurrence(
      Instant originStartAt, boolean generatedOrigin, Instant deletedAt) {
    RecurrenceEventOverride existing = findOverride(originStartAt).orElse(null);
    requireEligibleOccurrence(existing, generatedOrigin);
    return excludeOverride(existing, originStartAt, deletedAt);
  }

  public RecurrenceEventOverride excludeProviderOccurrence(
      Instant originStartAt, Instant deletedAt) {
    return excludeOverride(findOverride(originStartAt).orElse(null), originStartAt, deletedAt);
  }

  private RecurrenceEventOverride excludeOverride(
      RecurrenceEventOverride existing, Instant originStartAt, Instant deletedAt) {
    if (existing == null) {
      RecurrenceEventOverride created =
          RecurrenceEventOverride.deleted(this, originStartAt, deletedAt);
      overrides.add(created);
      return created;
    }
    existing.markDeleted(deletedAt);
    return existing;
  }

  public void removeOverrides(Collection<Instant> originStartAts) {
    overrides.removeIf(override -> originStartAts.contains(override.getOriginStartAt()));
  }

  private void requireEligibleOccurrence(
      RecurrenceEventOverride existing, boolean generatedOrigin) {
    if (existing == null && !generatedOrigin) {
      throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    }
  }
}
