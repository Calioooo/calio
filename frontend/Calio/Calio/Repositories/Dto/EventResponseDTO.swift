//
//  EventResponseDTO.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct TagResponseDTO: Decodable, Equatable {
    let id: Int64
    let title: String
    let colorCode: String
    let tagType: CalendarTagType
}

struct EventResponseDTO: Decodable {
    let id: Int64
    let title: String
    let description: String?
    let startAt: Date
    let endAt: Date
    let importantEvent: Bool
    let recurrenceId: Int64?
    let isRecurrenceOccurrence: Bool
    let tag: TagResponseDTO
    let createdAt: Date
    let updatedAt: Date

    init(
        id: Int64,
        title: String,
        description: String?,
        startAt: Date,
        endAt: Date,
        importantEvent: Bool = false,
        recurrenceId: Int64? = nil,
        isRecurrenceOccurrence: Bool = false,
        tag: TagResponseDTO = TagResponseDTO(
            id: CalendarTag.fallback.id,
            title: CalendarTag.fallback.title,
            colorCode: CalendarTag.fallback.colorCode,
            tagType: CalendarTag.fallback.tagType
        ),
        createdAt: Date,
        updatedAt: Date
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.importantEvent = importantEvent
        self.recurrenceId = recurrenceId
        self.isRecurrenceOccurrence = isRecurrenceOccurrence
        self.tag = tag
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    private enum CodingKeys: String, CodingKey {
        case id
        case title
        case description
        case startAt
        case endAt
        case importantEvent
        case recurrenceId
        case isRecurrenceOccurrence
        case tag
        case createdAt
        case updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        id = try container.decode(Int64.self, forKey: .id)
        title = try container.decode(String.self, forKey: .title)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        startAt = try container.decode(Date.self, forKey: .startAt)
        endAt = try container.decode(Date.self, forKey: .endAt)
        importantEvent = try container.decodeIfPresent(Bool.self, forKey: .importantEvent) ?? false
        recurrenceId = try container.decodeIfPresent(Int64.self, forKey: .recurrenceId)
        isRecurrenceOccurrence = try container.decodeIfPresent(Bool.self, forKey: .isRecurrenceOccurrence) ?? false
        tag = try container.decode(TagResponseDTO.self, forKey: .tag)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        updatedAt = try container.decode(Date.self, forKey: .updatedAt)
    }
}
