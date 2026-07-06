package com.calio.calendar.repository.entity;

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

    @Column
    private Instant completedAt;

    protected Task() {
    }

    public Task(String taskTitle) {
        this.taskTitle = taskTitle;
        this.completed = false;
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

    public void complete(Instant completedAt) {
        if (completed) {
            return;
        }

        this.completed = true;
        this.completedAt = completedAt;
    }

    public void uncomplete() {
        this.completed = false;
        this.completedAt = null;
    }
}
