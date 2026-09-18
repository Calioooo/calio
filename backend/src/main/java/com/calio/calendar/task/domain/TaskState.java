package com.calio.calendar.task.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

@Embeddable
public class TaskState {

  @Column(nullable = false)
  private boolean completed;

  @Column private Instant completedAt;

  protected TaskState() {}

  private TaskState(boolean completed, Instant completedAt) {
    this.completed = completed;
    this.completedAt = completedAt;
  }

  public static TaskState incomplete() {
    return new TaskState(false, null);
  }

  public TaskState complete(Instant completedAt) {
    if (completed) {
      return this;
    }
    return new TaskState(true, completedAt);
  }

  public TaskState uncomplete() {
    return incomplete();
  }

  public boolean isCompleted() {
    return completed;
  }

  public Instant completedAt() {
    return completedAt;
  }
}
