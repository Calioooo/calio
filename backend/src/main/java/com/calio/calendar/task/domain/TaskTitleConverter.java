package com.calio.calendar.task.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TaskTitleConverter implements AttributeConverter<TaskTitle, String> {

  @Override
  public String convertToDatabaseColumn(TaskTitle taskTitle) {
    return taskTitle == null ? null : taskTitle.value();
  }

  @Override
  public TaskTitle convertToEntityAttribute(String taskTitle) {
    return taskTitle == null ? null : new TaskTitle(taskTitle);
  }
}
