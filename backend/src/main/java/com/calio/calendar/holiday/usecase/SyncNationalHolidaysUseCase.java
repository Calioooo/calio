package com.calio.calendar.holiday.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.client.HolidayApiClient;
import com.calio.calendar.holiday.client.dto.HolidayApiItem;
import com.calio.calendar.holiday.client.dto.HolidayApiResponse;
import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.domain.NationalHolidayContent;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SyncNationalHolidaysUseCase {

  private static final Logger log = LoggerFactory.getLogger(SyncNationalHolidaysUseCase.class);
  private static final DateTimeFormatter PROVIDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  private final HolidayApiClient holidayApiClient;
  private final NationalHolidayRepository nationalHolidayRepository;
  private final TransactionTemplate transactionTemplate;

  public SyncNationalHolidaysUseCase(
      HolidayApiClient holidayApiClient,
      NationalHolidayRepository nationalHolidayRepository,
      TransactionTemplate transactionTemplate) {
    this.holidayApiClient = holidayApiClient;
    this.nationalHolidayRepository = nationalHolidayRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public void syncYearRange(int startYear, int endYear) {
    for (int year = startYear; year <= endYear; year++) {
      syncYear(year);
    }
  }

  private void syncYear(int year) {
    HolidayApiResponse response = null;
    try {
      response = holidayApiClient.fetchHolidays(year);
      if (!response.isSuccess()) {
        log.warn(
            "National holiday sync failed. year={} resultCode={}", year, response.resultCode());
        return;
      }

      Set<NationalHolidayContent> updatedHolidays =
          toNationalHolidayContents(year, response.items());
      if (updatedHolidays.isEmpty()) {
        log.warn("National holiday sync returned empty holiday entries. year={}", year);
        return;
      }

      transactionTemplate.executeWithoutResult(
          status -> replaceHolidaysForYear(year, updatedHolidays));
    } catch (Exception exception) {
      log.warn(
          "National holiday sync failed. year={} resultCode={} errorCode={} message={}",
          year,
          response == null ? null : response.resultCode(),
          errorCode(exception),
          exception.getMessage(),
          exception);
    }
  }

  private void replaceHolidaysForYear(int year, Set<NationalHolidayContent> updatedHolidays) {
    List<NationalHoliday> existingHolidays =
        nationalHolidayRepository.findByHolidayDateBetween(
            LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    Set<NationalHolidayContent> existingContents =
        existingHolidays.stream().map(NationalHolidayContent::from).collect(Collectors.toSet());
    List<NationalHolidayContent> holidaysToCreate =
        updatedHolidays.stream().filter(content -> !existingContents.contains(content)).toList();
    List<NationalHoliday> holidaysToDelete =
        existingHolidays.stream()
            .filter(holiday -> !updatedHolidays.contains(NationalHolidayContent.from(holiday)))
            .toList();

    nationalHolidayRepository.saveAllAndFlush(
        holidaysToCreate.stream()
            .map(content -> new NationalHoliday(content.holidayDate(), content.holidayTitle()))
            .toList());
    nationalHolidayRepository.deleteAll(holidaysToDelete);
  }

  private Set<NationalHolidayContent> toNationalHolidayContents(
      int year, List<HolidayApiItem> items) {
    return items.stream()
        .filter(item -> "Y".equals(item.isHoliday()))
        .map(this::toNationalHolidayContent)
        .peek(content -> requireRequestedYear(year, content))
        .collect(Collectors.toSet());
  }

  private NationalHolidayContent toNationalHolidayContent(HolidayApiItem item) {
    return new NationalHolidayContent(
        LocalDate.parse(item.localDate(), PROVIDER_DATE_FORMAT), item.dateName());
  }

  private void requireRequestedYear(int year, NationalHolidayContent content) {
    if (content.holidayDate().getYear() != year) {
      throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }
  }

  private String errorCode(Exception exception) {
    if (exception instanceof CalioException calioException) {
      return calioException.getErrorCode().name();
    }
    return null;
  }
}
