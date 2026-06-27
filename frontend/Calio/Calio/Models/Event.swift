//
//  Event.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import Foundation

struct Event: Identifiable {
    let id: Int64
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
    let colorCode: String
    let importantEvent: Bool
    let recurrenceId: Int64?
    let isRecurrenceOccurrence: Bool
    
    init(
        id: Int64 = 0,
        title: String,
        description: String,
        startAt: Date,
        endAt: Date,
        colorCode: String,
        importantEvent: Bool = false,
        recurrenceId: Int64? = nil,
        isRecurrenceOccurrence: Bool = false
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.colorCode = colorCode
        self.importantEvent = importantEvent
        self.recurrenceId = recurrenceId
        self.isRecurrenceOccurrence = isRecurrenceOccurrence
    }
}
