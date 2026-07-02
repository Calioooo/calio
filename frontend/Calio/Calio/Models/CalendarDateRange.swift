//
//  CalendarDateRange.swift
//  Calio
//
//  Created by Codex on 7/2/26.
//

import Foundation

struct CalendarDateRange: Equatable {
    let startDay: DayKey
    let endDay: DayKey

    init(startDay: DayKey, endDay: DayKey) {
        if startDay <= endDay {
            self.startDay = startDay
            self.endDay = endDay
        } else {
            self.startDay = endDay
            self.endDay = startDay
        }
    }

    func contains(_ day: DayKey) -> Bool {
        startDay <= day && day <= endDay
    }
}
