//
//  EventCreateInput.swift
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
}

struct RecurrenceEventCreateInput: Equatable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
    let recurrenceEndAt: Date
    let recurrenceFrequency: RecurrenceFrequency
}

enum CalendarEventCreationSubmitInput: Equatable {
    case single(EventCreateInput)
    case recurring(RecurrenceEventCreateInput)
}

enum RecurrenceFrequency: String, CaseIterable, Codable, Equatable {
    case daily = "DAILY"
    case weekly = "WEEKLY"
    case monthly = "MONTHLY"
    case yearly = "YEARLY"

    var koreanLabel: String {
        switch self {
        case .daily:
            return "매일"
        case .weekly:
            return "매주"
        case .monthly:
            return "매월"
        case .yearly:
            return "매년"
        }
    }
}
