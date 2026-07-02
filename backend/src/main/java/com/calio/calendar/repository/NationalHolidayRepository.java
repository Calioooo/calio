package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.NationalHoliday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NationalHolidayRepository extends JpaRepository<NationalHoliday, Long> {

    boolean existsByHolidayDateAndHolidayTitle(LocalDate holidayDate, String holidayTitle);

    List<NationalHoliday> findByHolidayDateBetween(LocalDate from, LocalDate to);

    List<NationalHoliday> findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(LocalDate from, LocalDate to);
}
