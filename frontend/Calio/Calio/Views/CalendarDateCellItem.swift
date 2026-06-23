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
}
