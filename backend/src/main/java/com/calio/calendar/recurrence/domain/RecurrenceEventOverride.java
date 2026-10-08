package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.domain.CanonicalSchedule;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public record RecurrenceEventOverride(
    @Column(name = "origin_start_at", nullable = false) Instant originStartAt,
    @Embedded
        @AttributeOverride(
            name = "value",
            column = @Column(name = "override_title", length = RecurrenceEventTitle.MAX_LENGTH))
        RecurrenceEventTitle overrideTitle,
    @Column(name = "override_description") String overrideDescription,
    @Embedded RecurrenceOverrideSchedule schedule,
    @Column(name = "deleted_at") Instant deletedAt) {

  public RecurrenceEventOverride {
    Objects.requireNonNull(originStartAt);
    if (deletedAt == null) {
      Objects.requireNonNull(overrideTitle);
      Objects.requireNonNull(schedule);
    } else if (overrideTitle != null || overrideDescription != null || schedule != null) {
      throw new IllegalArgumentException("제외된 회차는 변경 내용을 가질 수 없다.");
    }
  }

  static RecurrenceEventOverride active(
      Instant originStartAt, String title, String description, CanonicalSchedule schedule) {
    return new RecurrenceEventOverride(
        originStartAt,
        new RecurrenceEventTitle(title),
        description,
        RecurrenceOverrideSchedule.from(Objects.requireNonNull(schedule)),
        null);
  }

  static RecurrenceEventOverride deleted(Instant originStartAt, Instant deletedAt) {
    return new RecurrenceEventOverride(
        originStartAt, null, null, null, Objects.requireNonNull(deletedAt));
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public Instant getOriginStartAt() {
    return originStartAt;
  }

  public String getOverrideTitle() {
    return overrideTitle == null ? null : overrideTitle.value();
  }

  public String getOverrideDescription() {
    return overrideDescription;
  }

  public Instant getOverrideStartAt() {
    return schedule == null ? null : schedule.startAt();
  }

  public Instant getOverrideEndAt() {
    return schedule == null ? null : schedule.endAt();
  }

  public boolean isOverrideAllDay() {
    return schedule != null && schedule.allDay();
  }

  public String getOverrideTimeZone() {
    return schedule == null ? null : schedule.timeZone();
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }
}
