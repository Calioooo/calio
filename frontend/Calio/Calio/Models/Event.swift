//
//  Event.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import Foundation

enum CalendarTagType: String, Decodable, Equatable {
    case defaultTag = "DEFAULT"
    case custom = "CUSTOM"
}

struct CalendarTag: Identifiable, Equatable {
    let id: Int64
    let title: String
    let colorCode: String
    let tagType: CalendarTagType

    static let fallback = CalendarTag(
        id: 0,
        title: "기타",
        colorCode: "#64748B",
        tagType: .defaultTag
    )

    static func sample(colorCode: String, title: String = "기타") -> CalendarTag {
        CalendarTag(
            id: 0,
            title: title,
            colorCode: colorCode,
            tagType: .defaultTag
        )
    }
}

struct Event: Identifiable {
    let id: Int64
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
    let tag: CalendarTag
    let importantEvent: Bool
    let recurrenceId: Int64?
    let isRecurrenceOccurrence: Bool
    
    init(
        id: Int64 = 0,
        title: String,
        description: String,
        startAt: Date,
        endAt: Date,
        tag: CalendarTag = .fallback,
        importantEvent: Bool = false,
        recurrenceId: Int64? = nil,
        isRecurrenceOccurrence: Bool = false
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.tag = tag
        self.importantEvent = importantEvent
        self.recurrenceId = recurrenceId
        self.isRecurrenceOccurrence = isRecurrenceOccurrence
    }
}
