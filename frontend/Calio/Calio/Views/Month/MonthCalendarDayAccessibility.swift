//
//  MonthCalendarDayAccessibility.swift
//  Calio
//

import Foundation

enum MonthCalendarDayAccessibility {
    static func label(
        day: DayKey,
        weekday: CalendarWeekday?,
        isToday: Bool,
        isSelected: Bool,
        isInSelectedRange: Bool,
        isCurrentMonth: Bool,
        hasHoliday: Bool,
        eventCount: Int
    ) -> String {
        var components = ["\(day.month)월 \(day.day)일"]

        if let weekday {
            components.append(weekday.fullKoreanText)
        }
        if isToday {
            components.append("오늘")
        }
        if isSelected {
            components.append("선택됨")
        }
        if isInSelectedRange && !isSelected {
            components.append("선택 기간")
        }
        if !isCurrentMonth {
            components.append("이전 또는 다음 달")
        }
        if hasHoliday {
            components.append("공휴일")
        }
        if eventCount > 0 {
            components.append("일정 \(eventCount)개")
        }

        return components.joined(separator: ", ")
    }
}
