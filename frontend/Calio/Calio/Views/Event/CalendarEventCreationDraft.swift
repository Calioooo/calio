//
//  CalendarEventCreationDraft.swift
//  Calio
//
//  Created by Codex on 7/14/26.
//

import Foundation

struct CalendarEventCreationDraft: Equatable {
    var eventInput: EventInput
    var recurrenceInput: RecurrenceInput

    init(
        eventInput: EventInput,
        recurrenceInput: RecurrenceInput
    ) {
        self.eventInput = eventInput
        self.recurrenceInput = recurrenceInput
    }

    init(
        referenceDay: DayKey,
        initialDateRange: CalendarDateRange? = nil,
        tags: [CalendarTag] = [],
        calendar: Calendar = .current
    ) {
        let defaultTimeRange = Self.defaultTimeRange(
            referenceDay: referenceDay,
            calendar: calendar
        )
        let initialTimeRange = Self.timeRange(
            from: initialDateRange,
            defaultTimeRange: defaultTimeRange,
            calendar: calendar
        )

        eventInput = EventInput(
            title: "",
            startAt: initialTimeRange.startAt,
            endAt: initialTimeRange.endAt,
            isAllDay: false,
            description: "",
            tag: Self.defaultTag(from: tags)
        )
        recurrenceInput = RecurrenceInput(
            isEnabled: false,
            startDate: initialTimeRange.startAt,
            endDate: initialTimeRange.endAt,
            startTime: initialTimeRange.startAt,
            endTime: initialTimeRange.endAt,
            frequency: .daily
        )
    }

    var canSave: Bool {
        CalendarEventFormRules.canSave(
            title: eventInput.title,
            startAt: eventInput.startAt,
            endAt: eventInput.endAt,
            isRecurrenceEnabled: recurrenceInput.isEnabled,
            recurrenceStartDate: recurrenceInput.startDate,
            recurrenceEndDate: recurrenceInput.endDate,
            recurrenceStartTime: recurrenceInput.startTime,
            recurrenceEndTime: recurrenceInput.endTime,
            isAllDay: eventInput.isAllDay
        )
    }

    var submitInput: CalendarEventCreationSubmitInput {
        let title = eventInput.title.trimmingCharacters(in: .whitespacesAndNewlines)

        if recurrenceInput.isEnabled {
            return .recurring(
                RecurrenceEventCreateInput(
                    title: title,
                    description: eventInput.description,
                    recurrenceStartDate: recurrenceInput.startDate,
                    recurrenceEndDate: recurrenceInput.endDate,
                    recurrenceStartTime: recurrenceInput.startTime,
                    recurrenceEndTime: recurrenceInput.endTime,
                    recurrenceFrequency: recurrenceInput.frequency,
                    isAllDay: eventInput.isAllDay,
                    tagId: eventInput.tag?.id
                )
            )
        }

        return .single(
            EventCreateInput(
                title: title,
                description: eventInput.description,
                startAt: eventInput.startAt,
                endAt: eventInput.endAt,
                isAllDay: eventInput.isAllDay,
                tagId: eventInput.tag?.id
            )
        )
    }

    func applying(
        _ parseResult: LocalEventTextParseResult,
        calendar: Calendar
    ) -> CalendarEventCreationDraft {
        var draft = self
        draft.eventInput.title = parseResult.title
        draft.eventInput.isAllDay = parseResult.isAllDay

        if let startAt = parseResult.startAt {
            draft.eventInput.startAt = startAt
            draft.eventInput.endAt = parseResult.endAt ?? startAt.addingTimeInterval(3600)
        }

        guard let recurrenceFrequency = parseResult.recurrenceFrequency else {
            draft.recurrenceInput.isEnabled = false
            return draft
        }

        draft.recurrenceInput.isEnabled = true
        draft.recurrenceInput.frequency = recurrenceFrequency
        draft.recurrenceInput.startDate = draft.eventInput.startAt
        draft.recurrenceInput.endDate = calendar.date(
            byAdding: .year,
            value: 1,
            to: draft.eventInput.startAt
        ) ?? draft.eventInput.startAt
        draft.recurrenceInput.startTime = draft.eventInput.startAt
        draft.recurrenceInput.endTime = draft.eventInput.endAt
        return draft
    }

    func replacingTitle(with title: String) -> CalendarEventCreationDraft {
        var draft = self
        draft.eventInput.title = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return draft
    }

    static func defaultTimeRange(
        referenceDay: DayKey,
        calendar: Calendar
    ) -> (startAt: Date, endAt: Date) {
        let date = referenceDay.toDate(calendar: calendar)
        let startAt = calendar.date(
            bySettingHour: 9,
            minute: 0,
            second: 0,
            of: date
        ) ?? date
        let endAt = calendar.date(
            byAdding: .hour,
            value: 1,
            to: startAt
        ) ?? startAt

        return (startAt, endAt)
    }

    private static func timeRange(
        from dateRange: CalendarDateRange?,
        defaultTimeRange: (startAt: Date, endAt: Date),
        calendar: Calendar
    ) -> (startAt: Date, endAt: Date) {
        guard let dateRange else {
            return defaultTimeRange
        }

        return (
            startAt: date(
                for: dateRange.startDay,
                usingTimeFrom: defaultTimeRange.startAt,
                calendar: calendar
            ),
            endAt: date(
                for: dateRange.endDay,
                usingTimeFrom: defaultTimeRange.endAt,
                calendar: calendar
            )
        )
    }

    private static func date(
        for day: DayKey,
        usingTimeFrom timeSource: Date,
        calendar: Calendar
    ) -> Date {
        let date = day.toDate(calendar: calendar)
        let timeComponents = calendar.dateComponents([.hour, .minute, .second], from: timeSource)

        return calendar.date(
            bySettingHour: timeComponents.hour ?? 0,
            minute: timeComponents.minute ?? 0,
            second: timeComponents.second ?? 0,
            of: date
        ) ?? date
    }

    private static func defaultTag(from tags: [CalendarTag]) -> CalendarTag {
        tags.first { $0.title == "기타" } ?? tags.first ?? .fallback
    }
}
