//
//  EventInput.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import Foundation

struct EventCreateInput: Equatable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
    let isAllDay: Bool
    let tagId: Int64?

    init(
        title: String,
        description: String,
        startAt: Date,
        endAt: Date,
        isAllDay: Bool = false,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.isAllDay = isAllDay
        self.tagId = tagId
    }
}

struct EventUpdateInput: Equatable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
    let isAllDay: Bool
    let timeZone: String?
    let tagId: Int64?

    init(
        title: String,
        description: String,
        startAt: Date,
        endAt: Date,
        isAllDay: Bool = false,
        timeZone: String? = nil,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.isAllDay = isAllDay
        self.timeZone = timeZone
        self.tagId = tagId
    }
}

struct RecurrenceEventCreateInput: Equatable {
    let title: String
    let description: String
    let recurrenceStartDate: Date
    let recurrenceEndDate: Date
    let recurrenceStartTime: Date
    let recurrenceEndTime: Date
    let recurrenceFrequency: RecurrenceFrequency
    let isAllDay: Bool
    let tagId: Int64?

    init(
        title: String,
        description: String,
        recurrenceStartDate: Date,
        recurrenceEndDate: Date,
        recurrenceStartTime: Date,
        recurrenceEndTime: Date,
        recurrenceFrequency: RecurrenceFrequency,
        isAllDay: Bool = false,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.isAllDay = isAllDay
        self.tagId = tagId
    }
}

struct RecurrenceEventDetails: Equatable {
    let recurrenceId: Int64
    let title: String
    let description: String
    let recurrenceStartDate: Date
    let recurrenceEndDate: Date
    let recurrenceStartTime: Date
    let recurrenceEndTime: Date
    let recurrenceFrequency: RecurrenceFrequency
    let isAllDay: Bool
    let timeZone: String?
    let canUpdateSeries: Bool
    let isRuleEditable: Bool
    let tagId: Int64?

    init(
        recurrenceId: Int64,
        title: String,
        description: String,
        recurrenceStartDate: Date,
        recurrenceEndDate: Date,
        recurrenceStartTime: Date,
        recurrenceEndTime: Date,
        recurrenceFrequency: RecurrenceFrequency,
        isAllDay: Bool = false,
        timeZone: String? = nil,
        canUpdateSeries: Bool = false,
        isRuleEditable: Bool = false,
        tagId: Int64? = nil
    ) {
        self.recurrenceId = recurrenceId
        self.title = title
        self.description = description
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.isAllDay = isAllDay
        self.timeZone = timeZone
        self.canUpdateSeries = canUpdateSeries
        self.isRuleEditable = isRuleEditable
        self.tagId = tagId
    }
}

struct RecurrenceEventSeriesEditInput: Equatable {
    let title: String
    let description: String
    let recurrenceStartDate: Date
    let recurrenceEndDate: Date
    let recurrenceStartTime: Date
    let recurrenceEndTime: Date
    let recurrenceFrequency: RecurrenceFrequency
    let isAllDay: Bool
    let timeZone: String?
    let tagId: Int64?

    init(
        title: String,
        description: String,
        recurrenceStartDate: Date,
        recurrenceEndDate: Date,
        recurrenceStartTime: Date,
        recurrenceEndTime: Date,
        recurrenceFrequency: RecurrenceFrequency,
        isAllDay: Bool = false,
        timeZone: String? = nil,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.isAllDay = isAllDay
        self.timeZone = timeZone
        self.tagId = tagId
    }
}

struct RecurrenceEventUpdateInput: Equatable {
    let title: String
    let description: String
    let recurrenceStartDate: Date
    let recurrenceEndDate: Date
    let recurrenceStartTime: Date
    let recurrenceEndTime: Date
    let recurrenceFrequency: RecurrenceFrequency
    let isAllDay: Bool
    let timeZone: String?
    let tagId: Int64?

    init(
        title: String,
        description: String,
        recurrenceStartDate: Date,
        recurrenceEndDate: Date,
        recurrenceStartTime: Date,
        recurrenceEndTime: Date,
        recurrenceFrequency: RecurrenceFrequency,
        isAllDay: Bool = false,
        timeZone: String? = nil,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.isAllDay = isAllDay
        self.timeZone = timeZone
        self.tagId = tagId
    }
}

struct RecurrenceOccurrenceUpdateInput: Equatable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
    let isAllDay: Bool
    let timeZone: String?

    init(
        title: String = "",
        description: String = "",
        startAt: Date,
        endAt: Date,
        isAllDay: Bool = false,
        timeZone: String? = nil
    ) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.isAllDay = isAllDay
        self.timeZone = timeZone
    }
}

enum CalendarEventCreationSubmitInput: Equatable {
    case single(EventCreateInput)
    case recurring(RecurrenceEventCreateInput)
}
