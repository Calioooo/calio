//
//  CreateEventRequestDTO.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import Foundation

struct CreateEventRequestDTO: Encodable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
}

struct UpdateEventRequestDTO: Encodable, Equatable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
}

struct UpdateRecurrenceEventRequestDTO: Encodable, Equatable {
    let title: String?
    let description: String?
    let startAt: Date?
    let endAt: Date?
    let recurrenceFrequency: RecurrenceFrequency?
}

struct UpdateRecurrenceOccurrenceRequestDTO: Encodable, Equatable {
    let title: String?
    let description: String?
    let startAt: Date?
    let endAt: Date?
    let isImportant: Bool?
}

struct CreateRecurrenceEventRequestDTO: Encodable, Equatable {
    let recurrenceTitle: String
    let recurrenceDescription: String
    let recurrenceStartDate: String
    let recurrenceEndDate: String
    let recurrenceStartTime: String
    let recurrenceEndTime: String
    let recurrenceFrequency: RecurrenceFrequency
}

struct RecurrenceEventResponseDTO: Decodable, Equatable {
    let recurrenceId: Int64
    let recurrenceTitle: String
    let recurrenceDescription: String?
    let recurrenceStartDate: String
    let recurrenceEndDate: String
    let recurrenceStartTime: String
    let recurrenceEndTime: String
    let recurrenceFrequency: RecurrenceFrequency
}
