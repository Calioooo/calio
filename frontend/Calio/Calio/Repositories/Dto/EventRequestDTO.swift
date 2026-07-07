//
//  EventRequestDTO.swift
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
    let tagId: Int64?

    init(title: String, description: String, startAt: Date, endAt: Date, tagId: Int64? = nil) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.tagId = tagId
    }
}

struct UpdateEventRequestDTO: Encodable, Equatable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
    let tagId: Int64?

    init(title: String, description: String, startAt: Date, endAt: Date, tagId: Int64? = nil) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.tagId = tagId
    }
}

struct UpdateRecurrenceEventRequestDTO: Encodable, Equatable {
    let title: String?
    let description: String?
    let startAt: Date?
    let endAt: Date?
    let recurrenceFrequency: RecurrenceFrequency?
    let tagId: Int64?

    init(
        title: String?,
        description: String?,
        startAt: Date?,
        endAt: Date?,
        recurrenceFrequency: RecurrenceFrequency?,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.recurrenceFrequency = recurrenceFrequency
        self.tagId = tagId
    }
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
    let tagId: Int64?

    init(
        recurrenceTitle: String,
        recurrenceDescription: String,
        recurrenceStartDate: String,
        recurrenceEndDate: String,
        recurrenceStartTime: String,
        recurrenceEndTime: String,
        recurrenceFrequency: RecurrenceFrequency,
        tagId: Int64? = nil
    ) {
        self.recurrenceTitle = recurrenceTitle
        self.recurrenceDescription = recurrenceDescription
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.tagId = tagId
    }
}

struct CustomTagRequestDTO: Encodable, Equatable {
    let title: String
    let colorCode: String
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
    let tag: TagResponseDTO

    init(
        recurrenceId: Int64,
        recurrenceTitle: String,
        recurrenceDescription: String?,
        recurrenceStartDate: String,
        recurrenceEndDate: String,
        recurrenceStartTime: String,
        recurrenceEndTime: String,
        recurrenceFrequency: RecurrenceFrequency,
        tag: TagResponseDTO = TagResponseDTO(
            id: CalendarTag.fallback.id,
            title: CalendarTag.fallback.title,
            colorCode: CalendarTag.fallback.colorCode,
            tagType: CalendarTag.fallback.tagType
        )
    ) {
        self.recurrenceId = recurrenceId
        self.recurrenceTitle = recurrenceTitle
        self.recurrenceDescription = recurrenceDescription
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.tag = tag
    }
}
