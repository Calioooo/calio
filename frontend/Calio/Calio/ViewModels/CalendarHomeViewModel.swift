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
    
    private let initialGeneratedPastDays = 90
    private let initialGeneratedFutureDays = 90
    private let thresholdDays = 20
    private let generateFutureDays = 60
    private let generatePastDays = 60
    private let visibleDateCount = 7
    
    private let dateService: CalendarDateService
    private let eventService: EventService
    private let calendar: Calendar
    private var lastVisibleMonthKeys: Set<YearMonthKey> = []
    
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

    var focusedEventAreaState: CalendarEventAreaState {
        let key = YearMonthKey(day: state.focusedDay)
        let entry = state.monthEventCache[key] ?? .idle

        if entry.isLoading {
            return .loading
        }

        if let failure = entry.failure {
            return .failed(failure.message)
        }

        return .idle
    }
    
    func loadInitialIfNeeded() {
        guard state.isNeedInitialize() else { return }

        let focusedDate = state.focusedDay.toDate(calendar: calendar)
        replaceGeneratedDateCells(
            from: dateService.dateByAddingDays(days: initialGeneratedPastDays * -1, to: focusedDate),
            to: dateService.dateByAddingDays(days: initialGeneratedFutureDays, to: focusedDate)
        )
        prefetchFocusedMonthAndAdjacent(retryFailed: false)
    }
    
    func focusDay(_ day: DayKey) {
        guard state.focusedDay != day else {
            return
        }
        
        state = state.focused(on: day)
        ensureGeneratedDateCells(contain: day)
        prefetchFocusedMonthAndAdjacent(retryFailed: true)
    }
    
    func loadAdditionalEventsIfNeeded(visibleRange: CalendarVisibleIndexRange) {
        guard !state.daysByKey.isEmpty else {
            return
        }

        let visibleItems = loadedItems(in: visibleRange)
        appendGeneratedDateCellsIfNeeded(visibleRange: visibleRange)
        prefetchMonthsForVisibleItemsIfNeeded(visibleItems)
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

        guard let year = components.year,
              let month = components.month
        else {
            return
        }

        selectYearMonth(year: year, month: month)
    }

    func selectYearMonth(year: Int, month: Int) {
        let targetDay = makeTargetDayPreservingFocusedDay(year: year, month: month)
        state = state.focused(on: targetDay)
        ensureGeneratedDateCells(contain: targetDay)
        prefetchFocusedMonthAndAdjacent(retryFailed: true)
    }

    func retryFocusedMonthEvents() {
        requestMonths([YearMonthKey(day: state.focusedDay)], retryFailed: true)
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
            insertCreatedEventIntoMonthCache(createdEvent)
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
        from startDate: Date,
        to endDate: Date
    ) -> [DayKey: CalendarDateCellItem] {
        let eventsByDay = makeEventsByDay(cachedEvents(in: startDate...endDate))
        
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
    
    private func fetchMonth(_ key: YearMonthKey) {
        let range = key.dateRange(calendar: calendar)

        Task {
            do {
                let events = try await eventService.fetchEvents(from: range.from, to: range.to)
                self.setMonthCacheEntry(.loaded(sortedEvents(events)), for: key)
            } catch let error as EventServiceError {
                self.setFailedMonthCacheEntry(CalendarMonthEventFailure(error: error), for: key)
            } catch {
                self.setFailedMonthCacheEntry(.unexpected, for: key)
            }
        }
    }
    
    private func makeEventsByDay(_ events: [Event]) -> [DayKey: [Event]] {
        Dictionary(grouping: events) { event in
            DayKey(date: event.startAt, calendar: calendar)
        }
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

    private func replaceGeneratedDateCells(from startDate: Date, to endDate: Date) {
        state = state.replacingDateCells(
            startDate: startDate,
            endDate: endDate,
            daysByKey: makeDateCellItemsByDay(from: startDate, to: endDate)
        )
    }

    private func refreshGeneratedDateCells() {
        replaceGeneratedDateCells(from: state.startDate, to: state.endDate)
    }

    private func ensureGeneratedDateCells(contain day: DayKey) {
        let date = day.toDate(calendar: calendar)
        let normalizedDate = calendar.startOfDay(for: date)
        let normalizedStartDate = calendar.startOfDay(for: state.startDate)
        let normalizedEndDate = calendar.startOfDay(for: state.endDate)

        guard normalizedDate < normalizedStartDate || normalizedDate > normalizedEndDate else {
            refreshGeneratedDateCells()
            return
        }

        replaceGeneratedDateCells(
            from: dateService.dateByAddingDays(days: initialGeneratedPastDays * -1, to: date),
            to: dateService.dateByAddingDays(days: initialGeneratedFutureDays, to: date)
        )
    }

    private func appendGeneratedDateCellsIfNeeded(visibleRange: CalendarVisibleIndexRange) {
        if visibleRange.startIndex < thresholdDays {
            appendGeneratedDateCells(at: .start)
        }

        if loadedDateCount - visibleRange.endIndex < thresholdDays {
            appendGeneratedDateCells(at: .end)
        }
    }

    private func appendGeneratedDateCells(at edge: CalendarState.LoadedEdge) {
        let nextStartDate: Date
        let nextEndDate: Date

        switch edge {
        case .start:
            nextStartDate = dateService.dateByAddingDays(days: generatePastDays * -1, to: state.startDate)
            nextEndDate = dateService.dateByAddingDays(days: -1, to: state.startDate)
        case .end:
            nextStartDate = dateService.dateByAddingDays(days: 1, to: state.endDate)
            nextEndDate = dateService.dateByAddingDays(days: generateFutureDays, to: state.endDate)
        }

        let nextDaysByKey = makeDateCellItemsByDay(from: nextStartDate, to: nextEndDate)
        state = state.appended(
            startDate: nextStartDate,
            endDate: nextEndDate,
            daysByKey: nextDaysByKey
        )
    }

    private func prefetchFocusedMonthAndAdjacent(retryFailed: Bool) {
        requestMonths(
            adjacentMonthKeys(around: YearMonthKey(day: state.focusedDay)),
            retryFailed: retryFailed
        )
    }

    private func prefetchMonthsForVisibleItemsIfNeeded(_ visibleItems: [CalendarDateCellItem]) {
        let visibleMonthKeys = Set(visibleItems.map { YearMonthKey(day: $0.id) })

        guard !visibleMonthKeys.isEmpty,
              visibleMonthKeys != lastVisibleMonthKeys
        else {
            return
        }

        lastVisibleMonthKeys = visibleMonthKeys
        requestMonths(
            visibleMonthKeys.flatMap(adjacentMonthKeys(around:)),
            retryFailed: true
        )
    }

    private func requestMonths(_ keys: [YearMonthKey], retryFailed: Bool) {
        Set(keys).sorted().forEach { key in
            guard shouldFetchMonth(key, retryFailed: retryFailed) else {
                return
            }

            setMonthCacheEntry(.loading, for: key)
            fetchMonth(key)
        }
    }

    private func shouldFetchMonth(_ key: YearMonthKey, retryFailed: Bool) -> Bool {
        switch state.monthEventCache[key] ?? .idle {
        case .idle:
            return true
        case .failed:
            return retryFailed
        case .loading, .loaded:
            return false
        }
    }

    private func adjacentMonthKeys(around key: YearMonthKey) -> [YearMonthKey] {
        [
            key.addingMonths(-1, calendar: calendar),
            key,
            key.addingMonths(1, calendar: calendar)
        ]
    }

    private func setMonthCacheEntry(
        _ entry: CalendarMonthEventCacheEntry,
        for key: YearMonthKey
    ) {
        var nextCache = state.monthEventCache
        nextCache[key] = entry
        state = state.replacingMonthEventCache(nextCache)
        refreshGeneratedDateCells()
    }

    private func setFailedMonthCacheEntry(
        _ failure: CalendarMonthEventFailure,
        for key: YearMonthKey
    ) {
        guard state.monthEventCache[key]?.isLoading == true else {
            return
        }

        setMonthCacheEntry(.failed(failure), for: key)
    }

    private func cachedEvents(in dateRange: ClosedRange<Date>) -> [Event] {
        state.monthEventCache.values.flatMap(\.loadedEvents).filter { event in
            dateRange.contains(event.startAt)
        }
    }

    private func loadedItems(in visibleRange: CalendarVisibleIndexRange) -> [CalendarDateCellItem] {
        let items = loadedDateCellItems

        guard !items.isEmpty else {
            return []
        }

        let startIndex = max(visibleRange.startIndex, items.startIndex)
        let endIndex = min(visibleRange.endIndex, items.endIndex - 1)

        guard startIndex <= endIndex else {
            return []
        }

        return Array(items[startIndex...endIndex])
    }

    private func makeTargetDayPreservingFocusedDay(year: Int, month: Int) -> DayKey {
        let clampedDay = min(state.focusedDay.day, lastDayOfMonth(year: year, month: month))
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = clampedDay

        guard let date = calendar.date(from: components) else {
            preconditionFailure("Failed to create target day for \(year)-\(month)")
        }

        return DayKey(date: date, calendar: calendar)
    }

    private func lastDayOfMonth(year: Int, month: Int) -> Int {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = 1

        guard let firstDay = calendar.date(from: components),
              let dayRange = calendar.range(of: .day, in: .month, for: firstDay)
        else {
            preconditionFailure("Failed to find last day for \(year)-\(month)")
        }

        return dayRange.count
    }

    private func insertCreatedEventIntoMonthCache(_ event: Event) {
        let key = YearMonthKey(date: event.startAt, calendar: calendar)
        let currentEvents = state.monthEventCache[key]?.loadedEvents ?? []
        setMonthCacheEntry(.loaded(sortedEvents(currentEvents + [event])), for: key)
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
