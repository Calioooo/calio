//
//  EventInput.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import Foundation

struct EventInput: Equatable {
    var title: String
    var startAt: Date
    var endAt: Date
    var description: String
    var colorCode: String
}

struct RecurrenceInput: Equatable {
    var isEnabled: Bool
    var startDate: Date
    var endDate: Date
    var startTime: Date
    var endTime: Date
    var frequency: RecurrenceFrequency
}

struct EventCreateInput: Equatable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
}

struct EventUpdateInput: Equatable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
}

struct RecurrenceEventCreateInput: Equatable {
    let title: String
    let description: String
    let recurrenceStartDate: Date
    let recurrenceEndDate: Date
    let recurrenceStartTime: Date
    let recurrenceEndTime: Date
    let recurrenceFrequency: RecurrenceFrequency
}

enum CalendarEventCreationSubmitInput: Equatable {
    case single(EventCreateInput)
    case recurring(RecurrenceEventCreateInput)
}
