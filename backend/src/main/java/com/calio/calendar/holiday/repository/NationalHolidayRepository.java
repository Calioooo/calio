package com.calio.calendar.holiday.repository;

import com.calio.calendar.holiday.domain.NationalHoliday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NationalHolidayRepository extends JpaRepository<NationalHoliday, Long> {

  List<NationalHoliday> findByHolidayDateBetween(LocalDate from, LocalDate to);

  @Query(
      """
            select holiday
            from NationalHoliday holiday
            where holiday.holidayDate between :from and :to
            order by holiday.holidayDate asc, holiday.holidayTitle asc
            """)
  List<NationalHoliday> findAllInDateRange(
      @Param("from") LocalDate from, @Param("to") LocalDate to);
}
