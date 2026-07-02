package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CalendarItemResponse;
import com.calio.calendar.controller.dto.CalendarItemType;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.NationalHolidayRepository;
import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.NationalHoliday;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarItemService {

    private static final ZoneId NATIONAL_HOLIDAY_ZONE = ZoneId.of("Asia/Seoul");

    private final EventRepository eventRepository;
    private final NationalHolidayRepository nationalHolidayRepository;

    public CalendarItemService(
            EventRepository eventRepository,
            NationalHolidayRepository nationalHolidayRepository
    ) {
        this.eventRepository = eventRepository;
        this.nationalHolidayRepository = nationalHolidayRepository;
    }

    @Transactional(readOnly = true)
    public List<CalendarItemResponse> listCalendarItems(Instant from, Instant to) {
        validateTimeRange(from, to);
        List<CalendarItem> calendarItems = new ArrayList<>();
        calendarItems.addAll(findEventItems(from, to));
        calendarItems.addAll(findNationalHolidayItems(from, to));

        return calendarItems.stream()
                .sorted(calendarItemComparator())
                .map(CalendarItem::response)
                .toList();
    }

    private List<CalendarItem> findEventItems(Instant from, Instant to) {
        return eventRepository.findByStartAtBetweenAndDeletedAtIsNullOrderByStartAtAsc(from, to)
                .stream()
                .map(CalendarItem::event)
                .toList();
    }

    private List<CalendarItem> findNationalHolidayItems(Instant from, Instant to) {
        LocalDate holidayFrom = toNationalHolidayDate(from);
        LocalDate holidayTo = toNationalHolidayDate(to);
        return nationalHolidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(holidayFrom, holidayTo)
                .stream()
                .map(CalendarItem::nationalHoliday)
                .toList();
    }

    private LocalDate toNationalHolidayDate(Instant instant) {
        return instant.atZone(NATIONAL_HOLIDAY_ZONE).toLocalDate();
    }

    private void validateTimeRange(Instant from, Instant to) {
        if (!from.isAfter(to)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }

    private Comparator<CalendarItem> calendarItemComparator() {
        return Comparator.comparing(CalendarItem::sortDateTime)
                .thenComparingInt(CalendarItemService::itemTypeOrder)
                .thenComparing(CalendarItem::holidayTitle, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CalendarItem::eventId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static int itemTypeOrder(CalendarItem calendarItem) {
        if (calendarItem.itemType() == CalendarItemType.NATIONAL_HOLIDAY) {
            return 0;
        }

        return 1;
    }

    private record CalendarItem(
            Instant sortDateTime,
            CalendarItemType itemType,
            String holidayTitle,
            Long eventId,
            CalendarItemResponse response
    ) {

        private static CalendarItem event(Event event) {
            return new CalendarItem(
                    event.getStartAt(),
                    CalendarItemType.EVENT,
                    null,
                    event.getId(),
                    CalendarItemResponse.event(event)
            );
        }

        private static CalendarItem nationalHoliday(NationalHoliday nationalHoliday) {
            return new CalendarItem(
                    toSortDateTime(nationalHoliday),
                    CalendarItemType.NATIONAL_HOLIDAY,
                    nationalHoliday.getHolidayTitle(),
                    null,
                    CalendarItemResponse.nationalHoliday(nationalHoliday)
            );
        }

        private static Instant toSortDateTime(NationalHoliday nationalHoliday) {
            return nationalHoliday.getHolidayDate()
                    .atStartOfDay(NATIONAL_HOLIDAY_ZONE)
                    .toInstant();
        }
    }
}
