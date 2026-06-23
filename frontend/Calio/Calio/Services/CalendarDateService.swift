//
//  CalendarDateService.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct CalendarDateService {
    private let calendar: Calendar

    init(calendar: Calendar = .current) {
        self.calendar = calendar
    }

    func getWeekday(from date: Date) -> CalendarWeekday {
        let weekdayNumber = calendar.component(.weekday, from: date)

        guard let weekday = CalendarWeekday(rawValue: weekdayNumber) else {
            preconditionFailure("Failed to convert date to weekday: \(date)")
        }

        return weekday
    }
    
    func monthText(from date: Date) -> String {
        "\(calendar.component(.month, from: date))"
    }

    func dayText(from date: Date) -> String {
        "\(calendar.component(.day, from: date))"
    }

    func isToday(_ date: Date) -> Bool {
        calendar.isDateInToday(date)
    }
}
