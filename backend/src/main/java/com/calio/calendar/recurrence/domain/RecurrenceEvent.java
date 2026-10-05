package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 개인 반복 일정의 Aggregate Root. 회차 개별 변경의 목록과 생명주기를 소유한다. */
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

  @ElementCollection
  @CollectionTable(
      name = "recurrence_event_overrides",
      joinColumns = @JoinColumn(name = "recurrence_id"),
      uniqueConstraints =
          @UniqueConstraint(
              name = "uk_recurrence_event_overrides_recurrence_origin",
              columnNames = {"recurrence_id", "origin_start_at"}))
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
    update(title, description, schedule, recurrenceRules);
    this.tagId = tagId;
  }

  public void update(
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

  /** 현재 시리즈 정의 또는 보존된 개별 변경에서 작업 대상 회차를 확인한다. */
  public void requireOccurrence(Instant originStartAt, RecurrenceOriginMatcher originMatcher) {
    if (findOverride(originStartAt).isPresent()) {
      return;
    }
    if (!originMatcher.containsOrigin(schedule, recurrenceRules, originStartAt)) {
      throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    }
  }

  /** 확인된 회차의 변경 내용을 기록한다. 작업 대상 확인은 requireOccurrence 또는 수신 데이터 검증에서 수행한다. */
  public RecurrenceEventOverride changeOccurrence(
      Instant originStartAt, String title, String description, CanonicalSchedule schedule) {
    RecurrenceEventOverride changed =
        RecurrenceEventOverride.active(originStartAt, title, description, schedule);
    replaceOverride(changed);
    return changed;
  }

  /** 확인된 회차의 제외 상태를 불변 값으로 교체한다. */
  public RecurrenceEventOverride excludeOccurrence(Instant originStartAt, Instant deletedAt) {
    RecurrenceEventOverride excluded = RecurrenceEventOverride.deleted(originStartAt, deletedAt);
    replaceOverride(excluded);
    return excluded;
  }

  private void replaceOverride(RecurrenceEventOverride replacement) {
    for (int index = 0; index < overrides.size(); index++) {
      if (overrides.get(index).getOriginStartAt().equals(replacement.getOriginStartAt())) {
        overrides.set(index, replacement);
        return;
      }
    }
    overrides.add(replacement);
  }

  public void removeOverrides(Collection<Instant> originStartAts) {
    overrides.removeIf(override -> originStartAts.contains(override.getOriginStartAt()));
  }
}
