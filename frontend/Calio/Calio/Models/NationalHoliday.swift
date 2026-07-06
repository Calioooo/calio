//
//  NationalHoliday.swift
//  Calio
//
//  Created by Codex on 7/6/26.
//

import Foundation

struct NationalHoliday: Identifiable, Equatable {
    let id: Int64
    let day: DayKey
    let title: String

    func displayStartAt(calendar: Calendar = .current) -> Date {
        calendar.startOfDay(for: day.toDate(calendar: calendar))
    }

    func displayEndAt(calendar: Calendar = .current) -> Date {
        guard let nextDayStart = calendar.date(
            byAdding: .day,
            value: 1,
            to: displayStartAt(calendar: calendar)
        ) else {
            preconditionFailure("Failed to create holiday display end for: \(day)")
        }

        return calendar.startOfDay(for: nextDayStart)
    }
}
