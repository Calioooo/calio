package com.calio.calendar.task.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long taskId;

  @Column(nullable = false)
  private String taskTitle;

  @Column(nullable = false)
  private boolean completed = false;

  @Column private Instant completedAt;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  protected Task() {}

  public Task(String taskTitle, Long accountId) {
    this.taskTitle = taskTitle;
    this.completed = false;
    this.accountId = accountId;
  }

  public Long getTaskId() {
    return taskId;
  }

  public String getTaskTitle() {
    return taskTitle;
  }

  public boolean isCompleted() {
    return completed;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void updateTitle(String taskTitle) {
    if (completed) {
      throw new CalioException(ErrorCode.COMPLETED_TASK_TITLE_UPDATE_NOT_ALLOWED);
    }
    this.taskTitle = taskTitle;
  }

  public void changeCompleted(Instant completedAt) {
    if (completed) {
      return;
    }

    this.completed = true;
    this.completedAt = completedAt;
  }

  public void changeUncompleted() {
    this.completed = false;
    this.completedAt = null;
  }

  public Long getAccountId() {
    return accountId;
  }
}
