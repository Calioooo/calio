package com.calio.calendar.holiday.domain;

import com.calio.calendar.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "national_holidays")
public class NationalHoliday extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long nationalHolidayId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "holiday_title", nullable = false)
    private String holidayTitle;

    protected NationalHoliday() {
    }

    public NationalHoliday(LocalDate holidayDate, String holidayTitle) {
        this.holidayDate = holidayDate;
        this.holidayTitle = holidayTitle;
    }

    public Long getNationalHolidayId() {
        return nationalHolidayId;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getHolidayTitle() {
        return holidayTitle;
    }
}
