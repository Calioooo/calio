//
//  CalendarState.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct CalendarState {
    let startDate: Date
    let endDate: Date
    let daysByKey: [DayKey: CalendarDateCellItem]
    let focusedDay: DayKey
    
    enum LoadedEdge: Hashable {
        case start
        case end
    }
    
    func focused(on day: DayKey) -> CalendarState {
        return CalendarState(startDate: startDate, endDate: endDate, daysByKey: daysByKey, focusedDay: day)
    }
    
    func isNeedInitialize() -> Bool {
        return daysByKey.isEmpty
    }
    
    func nearLoadedEdge(
        around day: DayKey,
        thresholdDays: Int,
        calendar: Calendar
    ) -> LoadedEdge? {
        let date = day.toDate(calendar: calendar)
        let normalizedStartDate = calendar.startOfDay(for: startDate)
        let normalizedEndDate = calendar.startOfDay(for: endDate)
        let normalizedDate = calendar.startOfDay(for: date)
        
        let distanceFromStart = calendar.dateComponents(
            [.day],
            from: normalizedStartDate,
            to: normalizedDate
        ).day ?? 0
        
        let distanceToEnd = calendar.dateComponents(
            [.day],
            from: normalizedDate,
            to: normalizedEndDate
        ).day ?? 0
        
        if distanceFromStart < thresholdDays {
            return .start
        }
        
        if distanceToEnd < thresholdDays {
            return .end
        }
        
        return nil
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
            focusedDay: focusedDay
        )
    }
    
    func visibleDateCellItems(
        count: Int,
        calendar: Calendar
    ) -> [CalendarDateCellItem] {
        let focusedDate = focusedDay.toDate(calendar: calendar)
        let startDate = calendar.startOfDay(for: focusedDate)

        return sequence(first: startDate) { currentDate in
            calendar.date(byAdding: .day, value: 1, to: currentDate)
        }
        .prefix(count)
        .compactMap { date in
            let day = DayKey(date: date, calendar: calendar)
            return daysByKey[day]
        }
    }

    func loadedDateCellItems(calendar: Calendar) -> [CalendarDateCellItem] {
        daysByKey.values.sorted { earlierCandidate, laterCandidate in
            earlierCandidate.id.toDate(calendar: calendar) < laterCandidate.id.toDate(calendar: calendar)
        }
    }
}
