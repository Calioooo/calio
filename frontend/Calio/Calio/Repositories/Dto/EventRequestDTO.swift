//
//  EventRequestDTO.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import Foundation

struct CreateEventRequestDTO: Encodable {
    let title: String
    let description: String?
    let startAt: Date?
    let endAt: Date?
    let allDay: Bool
    let startDate: String?
    let endDate: String?
    let tagId: Int64?

    init(
        title: String,
        description: String?,
        startAt: Date?,
        endAt: Date?,
        allDay: Bool = false,
        startDate: String? = nil,
        endDate: String? = nil,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.allDay = allDay
        self.startDate = startDate
        self.endDate = endDate
        self.tagId = tagId
    }
}

struct UpdateEventRequestDTO: Encodable, Equatable {
    let title: String
    let description: String?
    let startAt: Date?
    let endAt: Date?
    let allDay: Bool
    let startDate: String?
    let endDate: String?
    let tagId: Int64?

    init(
        title: String,
        description: String?,
        startAt: Date?,
        endAt: Date?,
        allDay: Bool = false,
        startDate: String? = nil,
        endDate: String? = nil,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.allDay = allDay
        self.startDate = startDate
        self.endDate = endDate
        self.tagId = tagId
    }
}

struct UpdateRecurrenceEventRequestDTO: Encodable, Equatable {
    let title: String
    let description: String?
    let startDate: String
    let endDate: String
    let startTime: String?
    let endTime: String?
    let recurrenceFrequency: RecurrenceFrequency
    let allDay: Bool
    let tagId: Int64?

    init(
        title: String,
        description: String?,
        startDate: String,
        endDate: String,
        startTime: String?,
        endTime: String?,
        recurrenceFrequency: RecurrenceFrequency,
        allDay: Bool = false,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.startDate = startDate
        self.endDate = endDate
        self.startTime = startTime
        self.endTime = endTime
        self.recurrenceFrequency = recurrenceFrequency
        self.allDay = allDay
        self.tagId = tagId
    }
}

struct UpdateRecurrenceOccurrenceRequestDTO: Encodable, Equatable {
    let originStartAt: Date
    let startAt: Date?
    let endAt: Date?
    let startDate: String?
    let endDate: String?

    init(
        originStartAt: Date,
        startAt: Date?,
        endAt: Date?,
        startDate: String? = nil,
        endDate: String? = nil
    ) {
        self.originStartAt = originStartAt
        self.startAt = startAt
        self.endAt = endAt
        self.startDate = startDate
        self.endDate = endDate
    }
}

struct CreateRecurrenceEventRequestDTO: Encodable, Equatable {
    let recurrenceTitle: String
    let recurrenceDescription: String?
    let recurrenceStartDate: String
    let recurrenceEndDate: String
    let recurrenceStartTime: String?
    let recurrenceEndTime: String?
    let recurrenceFrequency: RecurrenceFrequency
    let allDay: Bool
    let tagId: Int64?

    init(
        recurrenceTitle: String,
        recurrenceDescription: String?,
        recurrenceStartDate: String,
        recurrenceEndDate: String,
        recurrenceStartTime: String?,
        recurrenceEndTime: String?,
        recurrenceFrequency: RecurrenceFrequency,
        allDay: Bool = false,
        tagId: Int64? = nil
    ) {
        self.recurrenceTitle = recurrenceTitle
        self.recurrenceDescription = recurrenceDescription
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.allDay = allDay
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
    let recurrenceStartTime: String?
    let recurrenceEndTime: String?
    let recurrenceFrequency: RecurrenceFrequency
    let allDay: Bool
    let tag: TagResponseDTO

    init(
        recurrenceId: Int64,
        recurrenceTitle: String,
        recurrenceDescription: String?,
        recurrenceStartDate: String,
        recurrenceEndDate: String,
        recurrenceStartTime: String?,
        recurrenceEndTime: String?,
        recurrenceFrequency: RecurrenceFrequency,
        allDay: Bool = false,
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
        self.allDay = allDay
        self.tag = tag
    }

    private enum CodingKeys: String, CodingKey {
        case recurrenceId
        case recurrenceTitle
        case recurrenceDescription
        case recurrenceStartDate
        case recurrenceEndDate
        case recurrenceStartTime
        case recurrenceEndTime
        case recurrenceFrequency
        case allDay
        case tag
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        recurrenceId = try container.decode(Int64.self, forKey: .recurrenceId)
        recurrenceTitle = try container.decode(String.self, forKey: .recurrenceTitle)
        recurrenceDescription = try container.decodeIfPresent(String.self, forKey: .recurrenceDescription)
        recurrenceStartDate = try container.decode(String.self, forKey: .recurrenceStartDate)
        recurrenceEndDate = try container.decode(String.self, forKey: .recurrenceEndDate)
        recurrenceStartTime = try container.decodeIfPresent(String.self, forKey: .recurrenceStartTime)
        recurrenceEndTime = try container.decodeIfPresent(String.self, forKey: .recurrenceEndTime)
        recurrenceFrequency = try container.decode(RecurrenceFrequency.self, forKey: .recurrenceFrequency)
        allDay = try container.decodeIfPresent(Bool.self, forKey: .allDay) ?? false
        tag = try container.decode(TagResponseDTO.self, forKey: .tag)
    }
}
