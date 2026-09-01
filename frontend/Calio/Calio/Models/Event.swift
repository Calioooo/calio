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
    let id: String
    let backendId: Int64?
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
    let isAllDay: Bool
    let timeZone: String?
    let tag: CalendarTag
    let importantEvent: Bool
    let recurrenceId: Int64?
    let isRecurrenceOccurrence: Bool
    let originStartAt: Date?

    var isRepeated: Bool {
        isRecurrenceOccurrence || recurrenceId != nil
    }
    
    init(
        id: Int64? = nil,
        title: String,
        description: String,
        startAt: Date,
        endAt: Date,
        isAllDay: Bool = false,
        timeZone: String? = nil,
        tag: CalendarTag = .fallback,
        importantEvent: Bool = false,
        recurrenceId: Int64? = nil,
        isRecurrenceOccurrence: Bool = false,
        originStartAt: Date? = nil
    ) {
        self.backendId = id
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.isAllDay = isAllDay
        self.timeZone = timeZone
        self.tag = tag
        self.importantEvent = importantEvent
        self.recurrenceId = recurrenceId
        self.isRecurrenceOccurrence = isRecurrenceOccurrence
        self.originStartAt = originStartAt
        self.id = Self.makeStableID(
            backendId: id,
            recurrenceId: recurrenceId,
            originStartAt: originStartAt,
            isRecurrenceOccurrence: isRecurrenceOccurrence
        )
    }

    private static func makeStableID(
        backendId: Int64?,
        recurrenceId: Int64?,
        originStartAt: Date?,
        isRecurrenceOccurrence: Bool
    ) -> String {
        if isRecurrenceOccurrence,
           let recurrenceId,
           let originStartAt {
            return "recurrence:\(recurrenceId):\(millisecondsSince1970(originStartAt))"
        }

        if let backendId {
            return "event:\(backendId)"
        }

        return "event:temporary"
    }

    private static func millisecondsSince1970(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1_000).rounded())
    }
}
