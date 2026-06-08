//
//  Events.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct Events {
    private(set) var eventsById: [Int64: Event]
    private(set) var eventsByDay: [DayKey: [Event]]
    
    
    private let calendar: Calendar
    
    init(events: [Event], calendar: Calendar = .current) {
        self.eventsById = Self.makeEventsById(from: events)
        self.eventsByDay = Self.makeEventsByDay(from: events, calendar: calendar)
        self.calendar = calendar
    }
    
    
    
    private static func makeEventsById(from events: [Event]) -> [Int64: Event] {
        events.reduce(into: [:]) { result, event in
            result[event.id] = event
        }
    }
    
    private static func makeEventsByDay(
        from events: [Event],
        calendar: Calendar
    ) -> [DayKey: [Event]] {
        Dictionary(grouping: events) {
            event in DayKey(date: event.startAt, calendar: calendar)
        }
    }
}
