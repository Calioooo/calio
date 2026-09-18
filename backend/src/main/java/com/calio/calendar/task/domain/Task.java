package com.calio.calendar.task.domain;

import com.calio.calendar.common.domain.BaseEntity;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
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

  @Convert(converter = TaskTitleConverter.class)
  @Column(name = "task_title", nullable = false, length = TaskTitle.MAX_LENGTH)
  private TaskTitle taskTitle;

  @Embedded private TaskState state;

  @Column(name = "account_id", nullable = false)
  private Long accountId;

  protected Task() {}

  public Task(TaskTitle taskTitle, Long accountId) {
    this.taskTitle = taskTitle;
    this.state = TaskState.incomplete();
    this.accountId = accountId;
  }

  public Long getTaskId() {
    return taskId;
  }

  public String getTaskTitle() {
    return taskTitle.value();
  }

  public boolean isCompleted() {
    return state.isCompleted();
  }

  public Instant getCompletedAt() {
    return state.completedAt();
  }

  public void updateTitle(TaskTitle taskTitle) {
    if (state.isCompleted()) {
      throw new CalioException(ErrorCode.COMPLETED_TASK_TITLE_UPDATE_NOT_ALLOWED);
    }
    this.taskTitle = taskTitle;
  }

  public void changeCompleted(Instant completedAt) {
    this.state = state.complete(completedAt);
  }

  public void changeUncompleted() {
    this.state = state.uncomplete();
  }

  public Long getAccountId() {
    return accountId;
  }
}
