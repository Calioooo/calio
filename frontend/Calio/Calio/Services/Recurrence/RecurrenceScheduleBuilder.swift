import Foundation

struct RecurrenceScheduleRequest: Equatable {
    let firstOccurrenceStartAt: Date
    let firstOccurrenceEndAt: Date
    let timeZone: String?
    let recurrence: [String]
}

enum RecurrenceScheduleBuilder {
    static func make(
        startDate: Date,
        endDate: Date?,
        startTime: Date,
        endTime: Date,
        frequency: RecurrenceFrequency,
        allDay: Bool,
        timeZone: TimeZone,
        formTimeZone: TimeZone = .current
    ) throws -> RecurrenceScheduleRequest {
        if allDay {
            let firstEndDate = Calendar(identifier: .gregorian).date(byAdding: .day, value: 1, to: startDate) ?? startDate
            let range = try CalendarDateService.utcAllDayRange(startAt: startDate, endAt: firstEndDate)
            return RecurrenceScheduleRequest(
                firstOccurrenceStartAt: range.startAt,
                firstOccurrenceEndAt: range.endAt,
                timeZone: nil,
                recurrence: [RecurrenceRule.make(frequency: frequency, until: endDate, allDay: true)]
            )
        }

        guard let firstStartAt = instant(
            date: startDate,
            time: startTime,
            timeZone: timeZone,
            formTimeZone: formTimeZone
        ), let firstEndAt = instant(
            date: startDate,
            time: endTime,
            timeZone: timeZone,
            formTimeZone: formTimeZone
        ), firstStartAt < firstEndAt else {
            throw EventServiceError.validationFailed
        }
        let until = try endDate.map { date in
            guard let value = instant(date: date, time: startTime, timeZone: timeZone, formTimeZone: formTimeZone) else {
                throw EventServiceError.validationFailed
            }
            return value
        }

        return RecurrenceScheduleRequest(
            firstOccurrenceStartAt: firstStartAt,
            firstOccurrenceEndAt: firstEndAt,
            timeZone: timeZone.identifier,
            recurrence: [RecurrenceRule.make(frequency: frequency, until: until, allDay: false)]
        )
    }

    private static func instant(
        date: Date,
        time: Date,
        timeZone: TimeZone,
        formTimeZone: TimeZone
    ) -> Date? {
        var sourceCalendar = Calendar(identifier: .gregorian)
        sourceCalendar.timeZone = formTimeZone
        let day = sourceCalendar.dateComponents([.year, .month, .day], from: date)
        let clock = sourceCalendar.dateComponents([.hour, .minute, .second], from: time)
        var targetCalendar = Calendar(identifier: .gregorian)
        targetCalendar.timeZone = timeZone
        guard let startOfDay = targetCalendar.date(from: day) else { return nil }
        let candidate = targetCalendar.nextDate(
            after: startOfDay.addingTimeInterval(-1),
            matching: clock,
            matchingPolicy: .strict,
            repeatedTimePolicy: .first,
            direction: .forward
        )
        guard let candidate,
              targetCalendar.dateComponents([.year, .month, .day], from: candidate) == day else {
            return nil
        }
        return candidate
    }
}
