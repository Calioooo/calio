//
//  CalendarState.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct YearMonthKey: Hashable, Comparable {
    let year: Int
    let month: Int

    init(year: Int, month: Int) {
        precondition((1...12).contains(month), "Invalid month: \(month)")
        self.year = year
        self.month = month
    }

    init(date: Date, calendar: Calendar = .current) {
        let components = calendar.dateComponents([.year, .month], from: date)

        guard let year = components.year,
              let month = components.month
        else {
            preconditionFailure("Failed to create YearMonthKey from date: \(date)")
        }

        self.init(year: year, month: month)
    }

    init(day: DayKey) {
        self.init(year: day.year, month: day.month)
    }

    static func < (lhs: YearMonthKey, rhs: YearMonthKey) -> Bool {
        if lhs.year != rhs.year {
            return lhs.year < rhs.year
        }

        return lhs.month < rhs.month
    }

    func addingMonths(_ value: Int, calendar: Calendar = .current) -> YearMonthKey {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = 1

        guard let monthStart = calendar.date(from: components),
              let movedDate = calendar.date(byAdding: .month, value: value, to: monthStart)
        else {
            preconditionFailure("Failed to move month from key: \(self)")
        }

        return YearMonthKey(date: movedDate, calendar: calendar)
    }

    func dateRange(calendar: Calendar = .current) -> (from: Date, to: Date) {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = 1

        guard let monthStart = calendar.date(from: components),
              let nextMonthStart = calendar.date(byAdding: .month, value: 1, to: monthStart)
        else {
            preconditionFailure("Failed to create date range from key: \(self)")
        }

        return (
            from: calendar.startOfDay(for: monthStart),
            to: nextMonthStart.addingTimeInterval(-0.001)
        )
    }
}

enum CalendarMonthEventCacheEntry {
    case idle
    case loading
    case loaded([Event])
    case failed(CalendarMonthEventFailure)

    var loadedEvents: [Event] {
        guard case .loaded(let events) = self else {
            return []
        }

        return events
    }

    var isLoading: Bool {
        guard case .loading = self else {
            return false
        }

        return true
    }

    var failure: CalendarMonthEventFailure? {
        guard case .failed(let failure) = self else {
            return nil
        }

        return failure
    }
}

enum CalendarMonthHolidayCacheEntry {
    case idle
    case loading
    case loaded([NationalHoliday])
    case failed(CalendarMonthHolidayFailure)

    var loadedHolidays: [NationalHoliday] {
        guard case .loaded(let holidays) = self else {
            return []
        }

        return holidays
    }

    var isLoading: Bool {
        guard case .loading = self else {
            return false
        }

        return true
    }
}

enum CalendarMonthEventFailure: Equatable {
    case network
    case unexpected

    init(error: EventServiceError) {
        switch error {
        case .network:
            self = .network
        case .eventNotFound,
             .recurrenceEventNotFound,
             .recurrenceOccurrenceNotFound,
             .validationFailed,
             .invalidTimeRange,
             .decoding,
             .unexpected:
            self = .unexpected
        }
    }

    var message: String {
        switch self {
        case .network:
            return "일정을 불러오지 못했습니다. 네트워크 연결을 확인해 주세요."
        case .unexpected:
            return "일정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
    }
}

enum CalendarMonthHolidayFailure: Equatable {
    case network
    case invalidHolidayDate
    case unexpected

    init(error: NationalHolidayServiceError) {
        switch error {
        case .network:
            self = .network
        case .invalidHolidayDate:
            self = .invalidHolidayDate
        case .decoding, .unexpected:
            self = .unexpected
        }
    }
}

enum CalendarEventAreaState: Equatable {
    case idle
    case loading
    case failed(String)
}

struct CalendarState {
    let startDate: Date
    let endDate: Date
    let daysByKey: [DayKey: CalendarDateCellItem]
    private let orderedDateCellItems: [CalendarDateCellItem]
    let monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]
    let monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry]
    
    enum LoadedEdge: Hashable {
        case start
        case end
    }

    init(
        startDate: Date,
        endDate: Date,
        daysByKey: [DayKey: CalendarDateCellItem],
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry] = [:],
        monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry] = [:]
    ) {
        self.startDate = startDate
        self.endDate = endDate
        self.daysByKey = daysByKey
        self.orderedDateCellItems = daysByKey.values.sorted { earlierCandidate, laterCandidate in
            earlierCandidate.id < laterCandidate.id
        }
        self.monthEventCache = monthEventCache
        self.monthHolidayCache = monthHolidayCache
    }

    private init(
        startDate: Date,
        endDate: Date,
        daysByKey: [DayKey: CalendarDateCellItem],
        orderedDateCellItems: [CalendarDateCellItem],
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry],
        monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry]
    ) {
        self.startDate = startDate
        self.endDate = endDate
        self.daysByKey = daysByKey
        self.orderedDateCellItems = orderedDateCellItems
        self.monthEventCache = monthEventCache
        self.monthHolidayCache = monthHolidayCache
    }
    
    func isNeedInitialize() -> Bool {
        return daysByKey.isEmpty && monthEventCache.isEmpty && monthHolidayCache.isEmpty
    }
    
    func appended(
        startDate newStartDate: Date,
        endDate newEndDate: Date,
        daysByKey newDaysByKey: [DayKey: CalendarDateCellItem],
        monthEventCache newMonthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]? = nil,
        monthHolidayCache newMonthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry]? = nil
    ) -> CalendarState {
        let mergedDaysByKey = daysByKey.merging(newDaysByKey) { current, _ in
            current
        }
        let newOrderedItems = newDaysByKey.values.sorted { earlierCandidate, laterCandidate in
            earlierCandidate.id < laterCandidate.id
        }
        let updatedOrderedItems: [CalendarDateCellItem]

        if newEndDate < startDate {
            updatedOrderedItems = newOrderedItems + orderedDateCellItems
        } else if newStartDate > endDate {
            updatedOrderedItems = orderedDateCellItems + newOrderedItems
        } else {
            updatedOrderedItems = mergedDaysByKey.values.sorted { earlierCandidate, laterCandidate in
                earlierCandidate.id < laterCandidate.id
            }
        }

        return CalendarState(
            startDate: min(startDate, newStartDate),
            endDate: max(endDate, newEndDate),
            daysByKey: mergedDaysByKey,
            orderedDateCellItems: updatedOrderedItems,
            monthEventCache: newMonthEventCache ?? monthEventCache,
            monthHolidayCache: newMonthHolidayCache ?? monthHolidayCache
        )
    }

    func replacingDateCells(
        startDate newStartDate: Date,
        endDate newEndDate: Date,
        daysByKey newDaysByKey: [DayKey: CalendarDateCellItem],
        monthEventCache newMonthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]? = nil,
        monthHolidayCache newMonthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry]? = nil
    ) -> CalendarState {
        CalendarState(
            startDate: newStartDate,
            endDate: newEndDate,
            daysByKey: newDaysByKey,
            monthEventCache: newMonthEventCache ?? monthEventCache,
            monthHolidayCache: newMonthHolidayCache ?? monthHolidayCache
        )
    }

    func replacingMonthEventCache(
        _ newMonthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry],
        updatingDateCells newDaysByKey: [DayKey: CalendarDateCellItem] = [:]
    ) -> CalendarState {
        guard !newDaysByKey.isEmpty else {
            return CalendarState(
                startDate: startDate,
                endDate: endDate,
                daysByKey: daysByKey,
                orderedDateCellItems: orderedDateCellItems,
                monthEventCache: newMonthEventCache,
                monthHolidayCache: monthHolidayCache
            )
        }

        let mergedDaysByKey = daysByKey.merging(newDaysByKey) { _, new in
            new
        }
        let updatedOrderedItems = orderedDateCellItems.map { item in
            newDaysByKey[item.id] ?? item
        }

        return CalendarState(
            startDate: startDate,
            endDate: endDate,
            daysByKey: mergedDaysByKey,
            orderedDateCellItems: updatedOrderedItems,
            monthEventCache: newMonthEventCache,
            monthHolidayCache: monthHolidayCache
        )
    }

    func replacingMonthHolidayCache(
        _ newMonthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry],
        updatingDateCells newDaysByKey: [DayKey: CalendarDateCellItem] = [:]
    ) -> CalendarState {
        guard !newDaysByKey.isEmpty else {
            return CalendarState(
                startDate: startDate,
                endDate: endDate,
                daysByKey: daysByKey,
                orderedDateCellItems: orderedDateCellItems,
                monthEventCache: monthEventCache,
                monthHolidayCache: newMonthHolidayCache
            )
        }

        let mergedDaysByKey = daysByKey.merging(newDaysByKey) { _, new in
            new
        }
        let updatedOrderedItems = orderedDateCellItems.map { item in
            newDaysByKey[item.id] ?? item
        }

        return CalendarState(
            startDate: startDate,
            endDate: endDate,
            daysByKey: mergedDaysByKey,
            orderedDateCellItems: updatedOrderedItems,
            monthEventCache: monthEventCache,
            monthHolidayCache: newMonthHolidayCache
        )
    }
    
    func loadedDateCellItems(calendar: Calendar) -> [CalendarDateCellItem] {
        return orderedDateCellItems
    }
}
