package com.calio.calendar.holiday.repository;

import com.calio.calendar.holiday.domain.NationalHoliday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NationalHolidayRepository extends JpaRepository<NationalHoliday, Long> {

    List<NationalHoliday> findByHolidayDateBetween(LocalDate from, LocalDate to);

    List<NationalHoliday> findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(LocalDate from, LocalDate to);
}
