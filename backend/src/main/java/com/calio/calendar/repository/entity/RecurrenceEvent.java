package com.calio.calendar.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "recurrence_events")
public class RecurrenceEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recurrenceTitle;

    @Column
    private String recurrenceDescription;

    @Column(nullable = false)
    private LocalDate recurrenceStartDate;

    @Column(nullable = false)
    private LocalDate recurrenceEndDate;

    @Column(nullable = false)
    private LocalTime recurrenceStartTime;

    @Column(nullable = false)
    private LocalTime recurrenceEndTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrenceFrequency recurrenceFrequency;

    protected RecurrenceEvent() {
    }

    public RecurrenceEvent(
            String recurrenceTitle,
            String recurrenceDescription,
            LocalDate recurrenceStartDate,
            LocalDate recurrenceEndDate,
            LocalTime recurrenceStartTime,
            LocalTime recurrenceEndTime,
            RecurrenceFrequency recurrenceFrequency
    ) {
        this.recurrenceTitle = recurrenceTitle;
        this.recurrenceDescription = recurrenceDescription;
        this.recurrenceStartDate = recurrenceStartDate;
        this.recurrenceEndDate = recurrenceEndDate;
        this.recurrenceStartTime = recurrenceStartTime;
        this.recurrenceEndTime = recurrenceEndTime;
        this.recurrenceFrequency = recurrenceFrequency;
    }

    public Long getId() {
        return id;
    }

    public String getRecurrenceTitle() {
        return recurrenceTitle;
    }

    public String getRecurrenceDescription() {
        return recurrenceDescription;
    }

    public LocalDate getRecurrenceStartDate() {
        return recurrenceStartDate;
    }

    public LocalDate getRecurrenceEndDate() {
        return recurrenceEndDate;
    }

    public LocalTime getRecurrenceStartTime() {
        return recurrenceStartTime;
    }

    public LocalTime getRecurrenceEndTime() {
        return recurrenceEndTime;
    }

    public RecurrenceFrequency getRecurrenceFrequency() {
        return recurrenceFrequency;
    }
}
