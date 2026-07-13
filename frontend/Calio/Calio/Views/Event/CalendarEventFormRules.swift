//
//  CalendarEventFormRules.swift
//  Calio
//
//  Created by Codex on 7/7/26.
//

import Foundation

enum CalendarEventFormRules {
    static func canSave(title: String, startAt: Date, endAt: Date) -> Bool {
        canSave(
            title: title,
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: false,
            recurrenceStartDate: startAt,
            recurrenceEndDate: startAt,
            recurrenceStartTime: startAt,
            recurrenceEndTime: endAt
        )
    }

    static func canSave(
        title: String,
        startAt: Date,
        endAt: Date,
        isRecurrenceEnabled: Bool,
        recurrenceStartDate: Date,
        recurrenceEndDate: Date,
        recurrenceStartTime: Date,
        recurrenceEndTime: Date
    ) -> Bool {
        guard !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }

        guard isRecurrenceEnabled else {
            return endAt > startAt
        }

        return !isUTCDate(recurrenceEndDate, before: recurrenceStartDate)
            && isUTCTime(recurrenceStartTime, before: recurrenceEndTime)
    }

    private static func isUTCDate(_ candidate: Date, before startAt: Date) -> Bool {
        DayKey(date: candidate, calendar: utcCalendar) < DayKey(date: startAt, calendar: utcCalendar)
    }

    private static func isUTCTime(_ startTime: Date, before endTime: Date) -> Bool {
        let calendar = utcCalendar
        let startComponents = calendar.dateComponents([.hour, .minute, .second], from: startTime)
        let endComponents = calendar.dateComponents([.hour, .minute, .second], from: endTime)
        let startSecond = totalSeconds(from: startComponents)
        let endSecond = totalSeconds(from: endComponents)

        return startSecond < endSecond
    }

    private static func totalSeconds(from components: DateComponents) -> Int {
        ((components.hour ?? 0) * 3600)
            + ((components.minute ?? 0) * 60)
            + (components.second ?? 0)
    }

    private static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }
}
