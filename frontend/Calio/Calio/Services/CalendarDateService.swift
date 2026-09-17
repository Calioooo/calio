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

    nonisolated static func utcDateString(from date: Date) -> String {
        let utcCalendar = makeUTCCalendar()
        let components = utcCalendar.dateComponents([.year, .month, .day], from: date)
        return String(
            format: "%04d-%02d-%02d",
            components.year ?? 0,
            components.month ?? 0,
            components.day ?? 0
        )
    }

    nonisolated static func localDateString(
        from date: Date,
        calendar: Calendar = .current
    ) -> String {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        return String(
            format: "%04d-%02d-%02d",
            components.year ?? 0,
            components.month ?? 0,
            components.day ?? 0
        )
    }

    nonisolated static func localDate(
        from string: String,
        calendar: Calendar = .current
    ) throws -> Date {
        let values = string.split(separator: "-", omittingEmptySubsequences: false)
        guard values.count == 3,
              let year = Int(values[0]),
              let month = Int(values[1]),
              let day = Int(values[2]) else {
            throw CalendarDateServiceError.invalidLocalDate
        }

        var components = DateComponents()
        components.calendar = calendar
        components.timeZone = calendar.timeZone
        components.year = year
        components.month = month
        components.day = day

        guard let date = calendar.date(from: components),
              calendar.dateComponents([.year, .month, .day], from: date) == DateComponents(
                year: year,
                month: month,
                day: day
              ) else {
            throw CalendarDateServiceError.invalidLocalDate
        }
        return date
    }

    nonisolated static func utcAllDayRange(
        startAt: Date,
        endAt: Date,
        calendar: Calendar = .current
    ) throws -> (startAt: Date, endAt: Date) {
        (
            try utcDate(from: localDateString(from: startAt, calendar: calendar)),
            try utcDate(from: localDateString(from: endAt, calendar: calendar))
        )
    }

    nonisolated static func localAllDayDisplayRange(
        utcStartAt: Date,
        utcEndAt: Date,
        calendar: Calendar = .current
    ) throws -> (startAt: Date, endAt: Date) {
        return (
            try localDate(from: utcDateString(from: utcStartAt), calendar: calendar),
            try localDate(from: utcDateString(from: utcEndAt), calendar: calendar)
        )
    }

    nonisolated static func utcTimeString(from date: Date) -> String {
        let utcCalendar = makeUTCCalendar()
        let components = utcCalendar.dateComponents([.hour, .minute, .second], from: date)
        return String(
            format: "%02d:%02d:%02d",
            components.hour ?? 0,
            components.minute ?? 0,
            components.second ?? 0
        )
    }

    nonisolated static func utcDate(from string: String) throws -> Date {
        let components = string.split(separator: "-").compactMap { Int($0) }

        guard components.count == 3 else {
            throw CalendarDateServiceError.invalidUTCDate
        }

        return try utcDate(year: components[0], month: components[1], day: components[2])
    }

    nonisolated static func utcTime(from string: String) throws -> Date {
        let components = string.split(separator: ":").compactMap { Int($0) }

        guard components.count >= 2 else {
            throw CalendarDateServiceError.invalidUTCTime
        }

        var dateComponents = DateComponents()
        dateComponents.calendar = makeUTCCalendar()
        dateComponents.timeZone = TimeZone(secondsFromGMT: 0)
        dateComponents.year = 1970
        dateComponents.month = 1
        dateComponents.day = 1
        dateComponents.hour = components[0]
        dateComponents.minute = components[1]
        dateComponents.second = components.count > 2 ? components[2] : 0

        guard let date = dateComponents.date else {
            throw CalendarDateServiceError.invalidUTCTime
        }

        return date
    }

    nonisolated static func composeUTCDateTime(date: Date, time: Date) throws -> Date {
        let calendar = makeUTCCalendar()
        let dateComponents = calendar.dateComponents([.year, .month, .day], from: date)
        let timeComponents = calendar.dateComponents([.hour, .minute, .second], from: time)

        return try utcDate(
            year: dateComponents.year,
            month: dateComponents.month,
            day: dateComponents.day,
            hour: timeComponents.hour,
            minute: timeComponents.minute,
            second: timeComponents.second
        )
    }

    private nonisolated static func utcDate(
        year: Int?,
        month: Int?,
        day: Int?,
        hour: Int? = 0,
        minute: Int? = 0,
        second: Int? = 0
    ) throws -> Date {
        guard let year, let month, let day else {
            throw CalendarDateServiceError.invalidUTCDate
        }

        var components = DateComponents()
        components.calendar = makeUTCCalendar()
        components.timeZone = TimeZone(secondsFromGMT: 0)
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = second

        guard let date = components.date else {
            throw CalendarDateServiceError.invalidUTCDate
        }

        return date
    }

    private nonisolated static func makeUTCCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }
}

enum CalendarDateServiceError: Error, Equatable {
    case invalidUTCDate
    case invalidUTCTime
    case invalidLocalDate
}
