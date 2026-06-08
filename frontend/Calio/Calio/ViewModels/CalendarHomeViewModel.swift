//
//  CalendarHomeViewModel.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

@MainActor
final class CalendarHomeViewModel: ObservableObject {
    @Published private(set) var state: CalendarState
    
    private let initialLoadPastDays = 60
    private let initialLoadFutureDays = 60
    
    private let thresholdDays = 20
    private let loadFutureDays = 60
    private let loadPastDays = 60
    
    private let visibleDateCount = 7
    
    private let dateService: CalendarDateService
    private let eventService: EventService
    private let calendar: Calendar
    
    init(
        calendar: Calendar = .current,
        dateService: CalendarDateService = CalendarDateService(),
        eventService: EventService = EventService()
    ) {
        self.dateService = dateService
        self.calendar = calendar
        self.eventService = eventService
        
        let focusedDate = Date()
        
        self.state = CalendarState(
            startDate: Date(),
            endDate: Date(),
            daysByKey: [:],
            focusedDay: DayKey(date: focusedDate, calendar: calendar)
        )
    }
    
    var visibleDateCellItems: [CalendarDateCellItem] {
        state.visibleDateCellItems(
            count: visibleDateCount,
            calendar: calendar
        )
    }
    
    func loadInitialIfNeeded() {
        guard state.isNeedInitialize() else { return }
        
        Task {
            let today = Date()
            let startDate = dateService.dateByAddingDays(days: initialLoadPastDays * -1, to: today)
            let endDate = dateService.dateByAddingDays(days: initialLoadFutureDays, to: today)
            
            do {
                let events = try await eventService.fetchEvents(from: startDate, to: endDate)
                let daysBykey = makeDateCellItemsByDay(
                    events: events,
                    from: startDate,
                    to: endDate
                )
                
                state = CalendarState(startDate: startDate, endDate: endDate, daysByKey: daysBykey, focusedDay: DayKey(date: today, calendar: calendar))
            } catch {
                print(error)
            }
        }
    }
    
    func focusDay(_ day: DayKey) {
        guard state.daysByKey[day] != nil else {
            return
        }
        
        state = state.focused(on: day)
        checkAndLoadEvents()
    }
    
    private func makeDateCellItemsByDay(
        events: [Event],
        from startDate: Date,
        to endDate: Date
    ) -> [DayKey: CalendarDateCellItem] {
        let eventsByDay = makeEventsByDay(events)
        
        return Dictionary(
            uniqueKeysWithValues: makeDates(from: startDate, to: endDate).map { date in
                let day = DayKey(date: date, calendar: calendar)
                
                return (
                    day,
                    CalendarDateCellItem(
                        id: day,
                        weekday: dateService.getWeekday(from: date),
                        monthText: dateService.monthText(from: date),
                        dayText: dateService.dayText(from: date),
                        isToday: dateService.isToday(date),
                        isSelected: false,
                        events: eventsByDay[day] ?? []
                    )
                )
            }
        )
    }
    
    private func checkAndLoadEvents() {
        guard let edge = state.nearLoadedEdge(
            around: state.focusedDay,
            thresholdDays: thresholdDays,
            calendar: calendar
        ) else {
            return
        }
        
        
        let loadStartDate: Date
        let loadEndDate: Date
        
        switch edge {
        case .end:
            loadStartDate = dateService.dateByAddingDays(days: 1, to: state.endDate)
            loadEndDate = dateService.dateByAddingDays(days: loadFutureDays, to: state.endDate)
            
        case .start:
            loadStartDate = dateService.dateByAddingDays(days: loadPastDays * -1, to: state.startDate)
            loadEndDate = dateService.dateByAddingDays(days: -1, to: state.startDate)
        }
        Task {
            do {
                let events = try await eventService.fetchEvents(from: loadStartDate, to: loadEndDate)
                let daysByKey = makeDateCellItemsByDay(events: events, from: loadStartDate, to: loadEndDate)
                self.state = self.state.appended(startDate: loadStartDate, endDate: loadEndDate, daysByKey: daysByKey)
            } catch {
                print(error)
            }
        }
    }
    
    private func makeEventsByDay(_ events: [Event]) -> [DayKey: [Event]] {
        Dictionary(grouping: events) { event in
            DayKey(date: event.startAt, calendar: calendar)
        }
    }
    
    private func makeDates(from startDate: Date, to endDate: Date) -> [Date] {
        let startOfDay = calendar.startOfDay(for: startDate)
        let endOfDay = calendar.startOfDay(for: endDate)
        
        return Array(
            sequence(first: startOfDay) { currentDate in
                self.calendar.date(byAdding: .day, value: 1, to: currentDate)
            }
                .prefix { currentDate in
                    currentDate <= endOfDay }
        )
    }
}
