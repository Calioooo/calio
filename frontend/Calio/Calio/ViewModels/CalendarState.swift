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
    case loadingPartial([Event])
    case partial([Event])
    case loaded([Event])
    case failed(CalendarMonthEventFailure)
    case failedPartial([Event], CalendarMonthEventFailure)

    var loadedEvents: [Event] {
        switch self {
        case .loadingPartial(let events), .partial(let events), .loaded(let events), .failedPartial(let events, _):
            return events
        case .idle, .loading, .failed(_):
            return []
        }
    }

    var isLoading: Bool {
        switch self {
        case .loading, .loadingPartial(_):
            return true
        case .idle, .partial(_), .loaded(_), .failed(_), .failedPartial(_, _):
            return false
        }
    }

    var failure: CalendarMonthEventFailure? {
        switch self {
        case .failed(let failure), .failedPartial(_, let failure):
            return failure
        case .idle, .loading, .loadingPartial(_), .partial(_), .loaded(_):
            return nil
        }
    }
}

enum CalendarMonthEventFailure: Equatable {
    case network
    case unexpected

    init(error: EventServiceError) {
        switch error {
        case .network:
            self = .network
        case .validationFailed, .invalidTimeRange, .decoding, .unexpected:
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

enum CalendarEventAreaState: Equatable {
    case idle
    case loading
    case failed(String)
}

struct CalendarState {
    let startDate: Date
    let endDate: Date
    let daysByKey: [DayKey: CalendarDateCellItem]
    let focusedDay: DayKey
    let monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]
    
    enum LoadedEdge: Hashable {
        case start
        case end
    }

    init(
        startDate: Date,
        endDate: Date,
        daysByKey: [DayKey: CalendarDateCellItem],
        focusedDay: DayKey,
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry] = [:]
    ) {
        self.startDate = startDate
        self.endDate = endDate
        self.daysByKey = daysByKey
        self.focusedDay = focusedDay
        self.monthEventCache = monthEventCache
    }
    
    func focused(on day: DayKey) -> CalendarState {
        return CalendarState(
            startDate: startDate,
            endDate: endDate,
            daysByKey: daysByKey,
            focusedDay: day,
            monthEventCache: monthEventCache
        )
    }
    
    func isNeedInitialize() -> Bool {
        return daysByKey.isEmpty && monthEventCache.isEmpty
    }
    
    func appended(
        startDate newStartDate: Date,
        endDate newEndDate: Date,
        daysByKey newDaysByKey: [DayKey: CalendarDateCellItem]
    ) -> CalendarState {
        CalendarState(
            startDate: min(startDate, newStartDate),
            endDate: max(endDate, newEndDate),
            daysByKey: daysByKey.merging(newDaysByKey) { current, _ in
                current
            },
            focusedDay: focusedDay,
            monthEventCache: monthEventCache
        )
    }

    func replacingDateCells(
        startDate newStartDate: Date,
        endDate newEndDate: Date,
        daysByKey newDaysByKey: [DayKey: CalendarDateCellItem]
    ) -> CalendarState {
        CalendarState(
            startDate: newStartDate,
            endDate: newEndDate,
            daysByKey: newDaysByKey,
            focusedDay: focusedDay,
            monthEventCache: monthEventCache
        )
    }

    func replacingMonthEventCache(
        _ newMonthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]
    ) -> CalendarState {
        CalendarState(
            startDate: startDate,
            endDate: endDate,
            daysByKey: daysByKey,
            focusedDay: focusedDay,
            monthEventCache: newMonthEventCache
        )
    }
    
    func loadedDateCellItems(calendar: Calendar) -> [CalendarDateCellItem] {
        daysByKey.values.sorted { earlierCandidate, laterCandidate in
            earlierCandidate.id.toDate(calendar: calendar) < laterCandidate.id.toDate(calendar: calendar)
        }
    }
}
