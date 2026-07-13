//
//  CalendarMonthEventLayout.swift
//  Calio
//
//  Created by Codex on 7/7/26.
//

import SwiftUI

struct MonthEventLayout {
    let spans: [MonthEventSpanItem]
    let hiddenCountByDay: [DayKey: Int]
}

struct MonthEventSpanItem: Identifiable {
    let chip: MonthScheduleChip
    let weekRowIndex: Int
    let startColumn: Int
    let columnSpan: Int
    let eventRowIndex: Int
    let days: [DayKey]

    var id: String {
        "\(chip.id)-\(weekRowIndex)-\(startColumn)-\(columnSpan)-\(eventRowIndex)"
    }
}

enum MonthScheduleChip {
    case event(Event)
    case holiday(NationalHoliday)
    
    var id: String {
        switch self {
        case .event(let event):
            return event.id
        case .holiday(let holiday):
            return "holiday-\(holiday.id)"
        }
    }
    
    var title: String {
        switch self {
        case .event(let event):
            return event.title
        case .holiday(let holiday):
            return holiday.title
        }
    }
    
    var color: Color {
        switch self {
        case .event(let event):
            return Color(hex: event.tag.colorCode)
        case .holiday:
            return Color.calendarHoliday
        }
    }
    
    var sortStartAt: Date {
        switch self {
        case .event(let event):
            return event.startAt
        case .holiday(let holiday):
            return holiday.displayStartAt(calendar: Calendar.current)
        }
    }
    
    var sortEndAt: Date {
        switch self {
        case .event(let event):
            return event.endAt
        case .holiday(let holiday):
            return holiday.displayEndAt(calendar: Calendar.current)
        }
    }
}

struct MonthEventLayoutBuilder {
    private let items: [CalendarDayItem]
    private let days: [DayKey]
    private let maxVisibleRowCount: Int
    private let calendar: Calendar
    private let columnCount = 7

    static func make(
        items: [CalendarDayItem],
        days: [DayKey],
        maxVisibleRowCount: Int,
        calendar: Calendar
    ) -> MonthEventLayout {
        MonthEventLayoutBuilder(
            items: items,
            days: days,
            maxVisibleRowCount: maxVisibleRowCount,
            calendar: calendar
        )
        .make()
    }

    private func make() -> MonthEventLayout {
        let rawSpans = makeRawSpans()
        let overflowDays = Set(
            rawSpans
                .filter { $0.eventRowIndex >= maxVisibleRowCount }
                .flatMap(\.days)
        )
        let visibleSpans = rawSpans.filter { span in
            guard span.eventRowIndex < maxVisibleRowCount else {
                return false
            }

            guard span.eventRowIndex == maxVisibleRowCount - 1 else {
                return true
            }

            return overflowDays.isDisjoint(with: span.days)
        }
        let visibleSpanIDs = Set(visibleSpans.map(\.id))
        let hiddenCountByDay = makeHiddenCountByDay(
            rawSpans: rawSpans,
            visibleSpanIDs: visibleSpanIDs
        )

        return MonthEventLayout(
            spans: visibleSpans,
            hiddenCountByDay: hiddenCountByDay
        )
    }

    private func makeRawSpans() -> [MonthEventSpanItem] {
        let chips = uniqueChips()
        var spans: [MonthEventSpanItem] = []
        var occupiedColumnsByWeekAndRow: [Int: [Int: Set<Int>]] = [:]

        for chip in chips {
            for weekRowIndex in 0..<weekRowCount {
                let weekDays = daysInWeekRow(weekRowIndex)
                let overlappingColumns = weekDays.enumerated().compactMap { column, day -> Int? in
                    chipOverlaps(chip, day: day) ? column : nil
                }

                guard let startColumn = overlappingColumns.min(),
                      let endColumn = overlappingColumns.max()
                else {
                    continue
                }

                let eventRowIndex = firstAvailableEventRowIndex(
                    from: startColumn,
                    to: endColumn,
                    weekRowIndex: weekRowIndex,
                    occupiedColumnsByWeekAndRow: occupiedColumnsByWeekAndRow
                )
                let coveredColumns = Set(startColumn...endColumn)
                occupiedColumnsByWeekAndRow[weekRowIndex, default: [:]][eventRowIndex] = (
                    occupiedColumnsByWeekAndRow[weekRowIndex]?[eventRowIndex] ?? []
                ).union(coveredColumns)

                spans.append(
                    MonthEventSpanItem(
                        chip: chip,
                        weekRowIndex: weekRowIndex,
                        startColumn: startColumn,
                        columnSpan: endColumn - startColumn + 1,
                        eventRowIndex: eventRowIndex,
                        days: Array(weekDays[startColumn...endColumn])
                    )
                )
            }
        }

        return spans
    }

    private func uniqueChips() -> [MonthScheduleChip] {
        var eventsByID: [String: Event] = [:]
        var holidaysByID: [Int64: NationalHoliday] = [:]

        for event in items.flatMap(\.events) {
            eventsByID[event.id] = event
        }

        for holiday in items.flatMap(\.holidays) {
            holidaysByID[holiday.id] = holiday
        }

        return (
            holidaysByID.values.map(MonthScheduleChip.holiday)
                + eventsByID.values.map(MonthScheduleChip.event)
        )
        .sorted { lhs, rhs in
            if lhs.sortStartAt != rhs.sortStartAt {
                return lhs.sortStartAt < rhs.sortStartAt
            }

            if lhs.sortEndAt != rhs.sortEndAt {
                return lhs.sortEndAt < rhs.sortEndAt
            }

            return lhs.id < rhs.id
        }
    }

    private func firstAvailableEventRowIndex(
        from startColumn: Int,
        to endColumn: Int,
        weekRowIndex: Int,
        occupiedColumnsByWeekAndRow: [Int: [Int: Set<Int>]]
    ) -> Int {
        let targetColumns = Set(startColumn...endColumn)
        var rowIndex = 0

        while true {
            let occupiedColumns = occupiedColumnsByWeekAndRow[weekRowIndex]?[rowIndex] ?? []

            if occupiedColumns.isDisjoint(with: targetColumns) {
                return rowIndex
            }

            rowIndex += 1
        }
    }

    private func makeHiddenCountByDay(
        rawSpans: [MonthEventSpanItem],
        visibleSpanIDs: Set<String>
    ) -> [DayKey: Int] {
        rawSpans.reduce(into: [DayKey: Int]()) { result, span in
            guard !visibleSpanIDs.contains(span.id) else {
                return
            }

            for day in span.days {
                result[day, default: 0] += 1
            }
        }
    }

    private var weekRowCount: Int {
        days.count / columnCount
    }

    private func daysInWeekRow(_ weekRowIndex: Int) -> [DayKey] {
        let startIndex = weekRowIndex * columnCount
        let endIndex = min(startIndex + columnCount, days.count)

        return Array(days[startIndex..<endIndex])
    }

    private func chipOverlaps(_ chip: MonthScheduleChip, day: DayKey) -> Bool {
        switch chip {
        case .event(let event):
            return eventOverlaps(event, day: day)
        case .holiday(let holiday):
            return holiday.day == day
        }
    }

    private func eventOverlaps(_ event: Event, day: DayKey) -> Bool {
        let dayStart = day.toDate(calendar: calendar)

        guard let nextDayStart = calendar.date(
            byAdding: .day,
            value: 1,
            to: dayStart
        ) else {
            return false
        }

        return event.startAt < nextDayStart && event.endAt > dayStart
    }
}
