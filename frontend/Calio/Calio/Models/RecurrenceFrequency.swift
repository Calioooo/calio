//
//  RecurrenceFrequency.swift
//  Calio
//
//  Created by Codex on 6/28/26.
//

import Foundation

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
