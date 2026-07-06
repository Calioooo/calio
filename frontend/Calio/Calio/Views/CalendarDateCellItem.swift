//
//  CalendarDateCellItem.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct CalendarDateCellItem: Identifiable {
    let id: DayKey
    let weekday: CalendarWeekday
    let monthText: String
    let dayText: String
    let isToday: Bool
    let events: [Event]
    let holidays: [NationalHoliday]

    var calendarItemCount: Int {
        events.count + holidays.count
    }

    var hasHoliday: Bool {
        !holidays.isEmpty
    }

    init(
        id: DayKey,
        weekday: CalendarWeekday,
        monthText: String,
        dayText: String,
        isToday: Bool,
        events: [Event],
        holidays: [NationalHoliday] = []
    ) {
        self.id = id
        self.weekday = weekday
        self.monthText = monthText
        self.dayText = dayText
        self.isToday = isToday
        self.events = events
        self.holidays = holidays
    }
}
