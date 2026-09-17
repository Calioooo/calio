import Testing
import Foundation
import SwiftUI
@testable import Calio

var fixedCalendar: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0)!
    return calendar
}
@MainActor
func waitUntil(
    timeoutNanoseconds: UInt64 = 5_000_000_000,
    condition: @escaping @MainActor () -> Bool
) async -> Bool {
    let retryIntervalNanoseconds: UInt64 = 10_000_000
    let maxAttemptCount = Int(timeoutNanoseconds / retryIntervalNanoseconds)

    for _ in 0...maxAttemptCount {
        if condition() {
            return true
        }

        try? await Task.sleep(nanoseconds: retryIntervalNanoseconds)
    }

    return condition()
}
func makeDayKey(dayOffset: Int, from baseDate: Date, calendar: Calendar) -> DayKey {
    let date = calendar.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate
    return DayKey(date: date, calendar: calendar)
}

func makeDateCellItem(
    dayOffset: Int,
    from baseDate: Date,
    calendar: Calendar,
    events: [Event] = [],
    holidays: [NationalHoliday] = []
) -> CalendarDayItem {
    let date = calendar.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate
    let dateService = CalendarDateService(calendar: calendar)

    return CalendarDayItem(
        id: DayKey(date: date, calendar: calendar),
        weekday: dateService.getWeekday(from: date),
        monthText: dateService.monthText(from: date),
        dayText: dateService.dayText(from: date),
        isToday: false,
        events: events,
        holidays: holidays
    )
}

func makeDateCellItem(
    date: Date,
    calendar: Calendar,
    events: [Event] = [],
    holidays: [NationalHoliday] = []
) -> CalendarDayItem {
    let dateService = CalendarDateService(calendar: calendar)

    return CalendarDayItem(
        id: DayKey(date: date, calendar: calendar),
        weekday: dateService.getWeekday(from: date),
        monthText: dateService.monthText(from: date),
        dayText: dateService.dayText(from: date),
        isToday: false,
        events: events,
        holidays: holidays
    )
}

func makeMonthGridDays(
    referenceDate: Date,
    calendar: Calendar
) -> [DayKey] {
    let monthComponents = calendar.dateComponents([.year, .month], from: referenceDate)
    guard let firstDayOfMonth = calendar.date(from: monthComponents) else {
        return []
    }

    let firstWeekdayIndex = calendar.component(.weekday, from: firstDayOfMonth) - 1
    guard let gridStartDate = calendar.date(
        byAdding: .day,
        value: firstWeekdayIndex * -1,
        to: firstDayOfMonth
    ) else {
        return []
    }

    return (0..<42).compactMap { offset in
        guard let date = calendar.date(
            byAdding: .day,
            value: offset,
            to: gridStartDate
        ) else {
            return nil
        }

        return DayKey(date: date, calendar: calendar)
    }
}

func makeTimelineMetrics() -> TimelineMetrics {
    TimelineMetrics(
        timeColumnWidth: 56,
        dayColumnWidth: 100,
        topBarHeight: 50,
        headerHeight: 90,
        fullDayEventRowHeight: 52,
        hourHeight: 60,
        visibleDayCount: 5,
        hourCount: 24,
        textScale: 1
    )
}

func makeLoadedState(
    dayOffsets: ClosedRange<Int>,
    from baseDate: Date,
    calendar: Calendar,
    monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry] = [:],
    monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry] = [:]
) -> CalendarState {
    let holidaysByDay = monthHolidayCache.values
        .flatMap(\.loadedHolidays)
        .reduce(into: [DayKey: [NationalHoliday]]()) { result, holiday in
            result[holiday.day, default: []].append(holiday)
        }
    let items = dayOffsets.map { offset in
        let day = makeDayKey(dayOffset: offset, from: baseDate, calendar: calendar)
        return makeDateCellItem(
            dayOffset: offset,
            from: baseDate,
            calendar: calendar,
            holidays: holidaysByDay[day] ?? []
        )
    }
    let startDate = calendar.date(byAdding: .day, value: dayOffsets.lowerBound, to: baseDate) ?? baseDate
    let endDate = calendar.date(byAdding: .day, value: dayOffsets.upperBound, to: baseDate) ?? baseDate

    return CalendarState(
        startDate: startDate,
        endDate: endDate,
        daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) }),
        monthEventCache: monthEventCache,
        monthHolidayCache: monthHolidayCache
    )
}

func makeEvent(
    id: Int64 = 1,
    title: String = "테스트 일정",
    on date: Date
) -> Event {
    Event(
        id: id,
        title: title,
        description: "",
        startAt: date,
        endAt: date.addingTimeInterval(3600),
        tag: .sample(colorCode: "#4F46E5")
    )
}

func makeEvent(
    id: Int64,
    title: String,
    startAt: Date,
    endAt: Date
) -> Event {
    Event(
        id: id,
        title: title,
        description: "",
        startAt: startAt,
        endAt: endAt,
        tag: .sample(colorCode: "#4F46E5")
    )
}

func makeEventResponse(from event: Event) -> EventResponseDTO {
    EventResponseDTO(
        id: event.backendId,
        title: event.title,
        description: event.description,
        startAt: event.startAt,
        endAt: event.endAt,
        tag: TagResponseDTO(
            id: event.tag.id,
            title: event.tag.title,
            colorCode: event.tag.colorCode,
            tagType: event.tag.tagType
        ),
        createdAt: event.startAt,
        updatedAt: event.startAt
    )
}
func makeNationalHolidayService(calendar: Calendar) -> NationalHolidayService {
    NationalHolidayService(
        repository: RecordingNationalHolidayRepository(),
        calendar: calendar
    )
}
