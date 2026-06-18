//
//  CalendarHomeViewModel.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct CalendarVisibleIndexRange: Equatable {
    let startIndex: Int
    let endIndex: Int
}

@MainActor
final class CalendarHomeViewModel: ObservableObject {
    @Published private(set) var state: CalendarState
    @Published private(set) var createState: CalendarEventCreateState = .idle
    
    private let initialLoadPastDays = 90
    private let initialLoadFutureDays = 90
    
    private let thresholdDays = 20
    private let loadFutureDays = 60
    private let loadPastDays = 60
    
    private let visibleDateCount = 7
    
    private let dateService: CalendarDateService
    private let eventService: EventService
    private let calendar: Calendar
    private var loadingEdges: Set<CalendarState.LoadedEdge> = []
    
    init(
        calendar: Calendar = .current,
        dateService: CalendarDateService = CalendarDateService(),
        eventService: EventService = EventService(),
        initialState: CalendarState? = nil,
        initialFocusedDate: Date = Date()
    ) {
        self.dateService = dateService
        self.calendar = calendar
        self.eventService = eventService
        
        self.state = initialState ?? CalendarState(
            startDate: initialFocusedDate,
            endDate: initialFocusedDate,
            daysByKey: [:],
            focusedDay: DayKey(date: initialFocusedDate, calendar: calendar)
        )
    }
    
    var visibleDateCellItems: [CalendarDateCellItem] {
        state.visibleDateCellItems(
            count: visibleDateCount,
            calendar: calendar
        )
    }

    var loadedDateCellItems: [CalendarDateCellItem] {
        state.loadedDateCellItems(calendar: calendar)
    }
    
    var loadedDateCount: Int {
        state.daysByKey.count
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
        
        guard state.focusedDay != day else {
            return
        }
        
        state = state.focused(on: day)
    }
    
    func loadAdditionalEventsIfNeeded(visibleRange: CalendarVisibleIndexRange) {
        guard !state.isNeedInitialize() else {
            return
        }
        
        if visibleRange.startIndex < thresholdDays {
            loadAdditionalEvents(at: .start)
        }
        
        if loadedDateCount - visibleRange.endIndex < thresholdDays {
            loadAdditionalEvents(at: .end)
        }
    }
    
    func moveMonth(by value: Int) {
        let currentDate = state.focusedDay.toDate(calendar: calendar)

        guard let movedMonthDate = calendar.date(
            byAdding: .month,
            value: value,
            to: currentDate
        ) else {
            return
        }

        let components = calendar.dateComponents([.year, .month], from: movedMonthDate)

        guard let firstDayOfMonth = calendar.date(from: components) else {
            return
        }

        let day = DayKey(date: firstDayOfMonth, calendar: calendar)

        focusDay(day)
    }

    func resetCreateState() {
        createState = .idle
    }

    func createEvent(_ input: EventCreateInput) async -> Bool {
        guard !createState.isSaving else {
            return false
        }

        createState = .saving

        do {
            let createdEvent = try await eventService.createEvent(input)
            insertCreatedEventIfLoaded(createdEvent)
            createState = .idle
            return true
        } catch let error as EventServiceError {
            createState = .failed(CalendarEventCreateFailure(error: error))
            return false
        } catch {
            createState = .failed(.unexpected)
            return false
        }
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
    
    private func loadAdditionalEvents(at edge: CalendarState.LoadedEdge) {
        guard !loadingEdges.contains(edge) else {
            return
        }
        
        loadingEdges.insert(edge)
        
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
            defer {
                self.loadingEdges.remove(edge)
            }
            
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

    private func insertCreatedEventIfLoaded(_ event: Event) {
        let eventDay = DayKey(date: event.startAt, calendar: calendar)

        guard isInsideLoadedRange(eventDay) else {
            return
        }

        let currentItem = state.daysByKey[eventDay] ?? makeDateCellItem(for: eventDay)
        var nextDaysByKey = state.daysByKey
        nextDaysByKey[eventDay] = CalendarDateCellItem(
            id: currentItem.id,
            weekday: currentItem.weekday,
            monthText: currentItem.monthText,
            dayText: currentItem.dayText,
            isToday: currentItem.isToday,
            isSelected: currentItem.isSelected,
            events: sortedEvents(currentItem.events + [event])
        )
        state = CalendarState(
            startDate: state.startDate,
            endDate: state.endDate,
            daysByKey: nextDaysByKey,
            focusedDay: state.focusedDay
        )
    }

    private func isInsideLoadedRange(_ day: DayKey) -> Bool {
        let date = day.toDate(calendar: calendar)
        let normalizedDate = calendar.startOfDay(for: date)
        let normalizedStartDate = calendar.startOfDay(for: state.startDate)
        let normalizedEndDate = calendar.startOfDay(for: state.endDate)

        return normalizedStartDate <= normalizedDate && normalizedDate <= normalizedEndDate
    }

    private func makeDateCellItem(for day: DayKey) -> CalendarDateCellItem {
        let date = day.toDate(calendar: calendar)

        return CalendarDateCellItem(
            id: day,
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: dateService.isToday(date),
            isSelected: false,
            events: []
        )
    }

    private func sortedEvents(_ events: [Event]) -> [Event] {
        events.sorted { lhs, rhs in
            if lhs.startAt != rhs.startAt {
                return lhs.startAt < rhs.startAt
            }

            if lhs.endAt != rhs.endAt {
                return lhs.endAt < rhs.endAt
            }

            return lhs.id < rhs.id
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

enum CalendarEventCreateState: Equatable {
    case idle
    case saving
    case failed(CalendarEventCreateFailure)

    var isSaving: Bool {
        self == .saving
    }

    var failureMessage: String? {
        guard case .failed(let failure) = self else {
            return nil
        }

        return failure.message
    }
}

enum CalendarEventCreateFailure: Equatable {
    case validationFailed
    case invalidTimeRange
    case network
    case unexpected

    init(error: EventServiceError) {
        switch error {
        case .validationFailed:
            self = .validationFailed
        case .invalidTimeRange:
            self = .invalidTimeRange
        case .network:
            self = .network
        case .decoding, .unexpected:
            self = .unexpected
        }
    }

    var message: String {
        switch self {
        case .validationFailed:
            return "입력값을 확인해 주세요."
        case .invalidTimeRange:
            return "종료 시각은 시작 시각보다 늦어야 합니다."
        case .network:
            return "서버에 연결할 수 없습니다."
        case .unexpected:
            return "일정을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
    }
}
