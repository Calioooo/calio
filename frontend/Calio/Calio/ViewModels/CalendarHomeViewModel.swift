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
    @Published private(set) var referenceDay: DayKey
    @Published private(set) var createState: CalendarEventCreateState = .idle
    @Published private(set) var mutationState: CalendarEventMutationState = .idle
    
    private let initialGeneratedPastMonths = 3
    private let initialGeneratedFutureMonths = 3
    private let edgeGenerationThresholdDateCount = 20
    private let generatedMonthBatchCount = 2
    private let maxGeneratedMonthCount = 18
    
    private let dateService: CalendarDateService
    private let eventService: EventService
    private let calendar: Calendar
    private var lastVisibleMonthKeys: Set<YearMonthKey> = []
    private var lastHandledVisibleRange: CalendarVisibleIndexRange?
    private var monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]
    private var pendingCreatedEventsByMonth: [YearMonthKey: [Event]] = [:]
    
    init(
        calendar: Calendar = .current,
        dateService: CalendarDateService = CalendarDateService(),
        eventService: EventService = EventService(),
        initialState: CalendarState? = nil,
        initialReferenceDate: Date = Date()
    ) {
        self.dateService = dateService
        self.calendar = calendar
        self.eventService = eventService
        self.monthEventCache = initialState?.monthEventCache ?? [:]
        let referenceDate = initialState?.startDate ?? initialReferenceDate
        self.referenceDay = DayKey(date: referenceDate, calendar: calendar)
        
        self.state = initialState ?? CalendarState(
            startDate: initialReferenceDate,
            endDate: initialReferenceDate,
            daysByKey: [:]
        )
    }
    
    var loadedDateCellItems: [CalendarDateCellItem] {
        return state.loadedDateCellItems(calendar: calendar)
    }
    
    var loadedDateCount: Int {
        state.daysByKey.count
    }

    var referenceEventAreaState: CalendarEventAreaState {
        let key = YearMonthKey(day: referenceDay)
        let entry = monthEventCache[key] ?? .idle

        if entry.isLoading {
            return .loading
        }

        if let failure = entry.failure {
            return .failed(failure.message)
        }

        return .idle
    }
    
    func loadInitialIfNeeded() {
        guard state.daysByKey.isEmpty && monthEventCache.isEmpty else { return }

        let referenceDate = referenceDay.toDate(calendar: calendar)
        let range = initialGeneratedDateRange(around: referenceDate)
        replaceGeneratedDateCells(from: range.startDate, to: range.endDate)
        prefetchReferenceMonthAndAdjacent(retryFailed: false)
    }
    
    func setReferenceDay(_ day: DayKey) {
        guard referenceDay != day else {
            return
        }
        
        referenceDay = day
        ensureGeneratedDateCells(contain: day)
    }
    
    func loadAdditionalEventsIfNeeded(visibleRange: CalendarVisibleIndexRange) {
        guard !state.daysByKey.isEmpty else {
            return
        }

        guard lastHandledVisibleRange != visibleRange else {
            return
        }

        lastHandledVisibleRange = visibleRange

        let visibleItems = loadedItems(in: visibleRange)
        appendGeneratedDateCellsIfNeeded(visibleRange: visibleRange)
        prefetchMonthsForVisibleItemsIfNeeded(visibleItems)
    }
    
    func moveMonth(by value: Int) {
        let currentDate = referenceDay.toDate(calendar: calendar)

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
        let targetDay = makeTargetDayPreservingReferenceDay(year: year, month: month)
        referenceDay = targetDay
        ensureGeneratedDateCells(contain: targetDay)
        prefetchReferenceMonthAndAdjacent(retryFailed: true)
    }

    func retryReferenceMonthEvents() {
        requestMonths([YearMonthKey(day: referenceDay)], retryFailed: true)
    }

    func resetCreateState() {
        createState = .idle
    }

    func resetMutationState() {
        mutationState = .idle
    }

    func createEvent(_ input: CalendarEventCreationSubmitInput) async -> Bool {
        switch input {
        case .single(let eventInput):
            return await createEvent(eventInput)
        case .recurring(let recurrenceInput):
            return await createRecurrenceEvent(recurrenceInput)
        }
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

    private func createRecurrenceEvent(_ input: RecurrenceEventCreateInput) async -> Bool {
        guard !createState.isSaving else {
            return false
        }

        createState = .saving

        do {
            try await eventService.createRecurrenceEvent(input)
            invalidateMonthEventCache()
            refetchDefaultPrefetchRange()
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

    func updateSingleEvent(_ event: Event, input: EventUpdateInput) async -> Bool {
        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            let updatedEvent = try await eventService.updateEvent(eventId: event.id, input: input)
            invalidateAndRefetchMonths([
                YearMonthKey(date: event.startAt, calendar: calendar),
                YearMonthKey(date: updatedEvent.startAt, calendar: calendar)
            ])
            mutationState = .idle
            return true
        } catch let error as EventServiceError {
            mutationState = .failed(CalendarEventMutationFailure(error: error))
            return false
        } catch {
            mutationState = .failed(.unexpected)
            return false
        }
    }

    func fetchRecurrenceEvent(recurrenceId: Int64) async -> RecurrenceEventDetails? {
        guard !mutationState.isMutating else {
            return nil
        }

        mutationState = .saving

        do {
            let details = try await eventService.fetchRecurrenceEvent(recurrenceId: recurrenceId)
            mutationState = .idle
            return details
        } catch let error as EventServiceError {
            mutationState = .failed(CalendarEventMutationFailure(error: error))
            return nil
        } catch {
            mutationState = .failed(.unexpected)
            return nil
        }
    }

    func updateRecurrenceOccurrence(_ event: Event, input: EventUpdateInput) async -> Bool {
        guard let recurrenceId = event.recurrenceId else {
            return false
        }

        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            _ = try await eventService.updateRecurrenceOccurrence(
                recurrenceId: recurrenceId,
                eventId: event.id,
                input: RecurrenceOccurrenceUpdateInput(
                    title: input.title,
                    description: input.description,
                    startAt: input.startAt,
                    endAt: input.endAt,
                    isImportant: event.importantEvent
                )
            )
            invalidateMonthEventCache()
            refetchDefaultPrefetchRange()
            mutationState = .idle
            return true
        } catch let error as EventServiceError {
            mutationState = .failed(CalendarEventMutationFailure(error: error))
            return false
        } catch {
            mutationState = .failed(.unexpected)
            return false
        }
    }

    func updateRecurrenceSeries(
        recurrenceId: Int64,
        input: RecurrenceEventSeriesEditInput
    ) async -> Bool {
        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            _ = try await eventService.updateRecurrenceEvent(
                recurrenceId: recurrenceId,
                input: RecurrenceEventUpdateInput(
                    title: input.title,
                    description: input.description,
                    startAt: try EventService.composeUTCDateTime(
                        date: input.recurrenceStartDate,
                        time: input.recurrenceStartTime
                    ),
                    endAt: try EventService.composeUTCDateTime(
                        date: input.recurrenceEndDate,
                        time: input.recurrenceEndTime
                    ),
                    recurrenceFrequency: input.recurrenceFrequency
                )
            )
            invalidateMonthEventCache()
            refetchDefaultPrefetchRange()
            mutationState = .idle
            return true
        } catch let error as EventServiceError {
            mutationState = .failed(CalendarEventMutationFailure(error: error))
            return false
        } catch {
            mutationState = .failed(.unexpected)
            return false
        }
    }

    func deleteSingleEvent(_ event: Event) async -> Bool {
        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            try await eventService.deleteEvent(eventId: event.id)
            invalidateAndRefetchMonths([YearMonthKey(date: event.startAt, calendar: calendar)])
            mutationState = .idle
            return true
        } catch let error as EventServiceError {
            mutationState = .failed(CalendarEventMutationFailure(error: error))
            return false
        } catch {
            mutationState = .failed(.unexpected)
            return false
        }
    }

    func deleteRecurrenceOccurrence(_ event: Event) async -> Bool {
        guard let recurrenceId = event.recurrenceId else {
            return false
        }

        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            try await eventService.deleteRecurrenceOccurrence(
                recurrenceId: recurrenceId,
                eventId: event.id
            )
            invalidateMonthEventCache()
            refetchDefaultPrefetchRange()
            mutationState = .idle
            return true
        } catch let error as EventServiceError {
            mutationState = .failed(CalendarEventMutationFailure(error: error))
            return false
        } catch {
            mutationState = .failed(.unexpected)
            return false
        }
    }

    func deleteRecurrenceSeries(_ event: Event) async -> Bool {
        guard let recurrenceId = event.recurrenceId else {
            return false
        }

        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            try await eventService.deleteRecurrenceEvent(recurrenceId: recurrenceId)
            invalidateMonthEventCache()
            refetchDefaultPrefetchRange()
            mutationState = .idle
            return true
        } catch let error as EventServiceError {
            mutationState = .failed(CalendarEventMutationFailure(error: error))
            return false
        } catch {
            mutationState = .failed(.unexpected)
            return false
        }
    }
    
    private func makeDateCellItemsByDay(
        from startDate: Date,
        to endDate: Date,
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]? = nil
    ) -> [DayKey: CalendarDateCellItem] {
        let eventsByDay = makeEventsByDay(
            cachedEvents(
                in: startDate...endDate,
                monthEventCache: monthEventCache ?? self.monthEventCache
            )
        )
        
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
                let pendingEvents = self.pendingCreatedEventsByMonth.removeValue(forKey: key) ?? []
                self.setMonthCacheEntry(
                    .loaded(self.mergedSortedEvents(events, with: pendingEvents)),
                    for: key
                )
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

    private func replaceGeneratedDateCells(from startDate: Date, to endDate: Date) {
        state = state.replacingDateCells(
            startDate: startDate,
            endDate: endDate,
            daysByKey: makeDateCellItemsByDay(from: startDate, to: endDate),
            monthEventCache: monthEventCache
        )
    }

    private func refreshGeneratedDateCells(for key: YearMonthKey) {
        let daysByKey = makeDateCellItemsForGeneratedMonth(
            key,
            monthEventCache: monthEventCache
        )

        guard !daysByKey.isEmpty else {
            return
        }

        state = state.replacingMonthEventCache(
            monthEventCache,
            updatingDateCells: daysByKey
        )
    }

    private func initialGeneratedDateRange(around date: Date) -> (startDate: Date, endDate: Date) {
        let referenceMonthKey = YearMonthKey(date: date, calendar: calendar)
        let startKey = referenceMonthKey.addingMonths(
            initialGeneratedPastMonths * -1,
            calendar: calendar
        )
        let endKey = referenceMonthKey.addingMonths(
            initialGeneratedFutureMonths,
            calendar: calendar
        )

        return monthDateRange(from: startKey, to: endKey)
    }

    private func monthDateRange(
        from startKey: YearMonthKey,
        to endKey: YearMonthKey
    ) -> (startDate: Date, endDate: Date) {
        let startRange = startKey.dateRange(calendar: calendar)
        let endRange = endKey.dateRange(calendar: calendar)

        return (startDate: startRange.from, endDate: endRange.to)
    }

    private func ensureGeneratedDateCells(contain day: DayKey) {
        let date = day.toDate(calendar: calendar)
        let normalizedDate = calendar.startOfDay(for: date)
        let normalizedStartDate = calendar.startOfDay(for: state.startDate)
        let normalizedEndDate = calendar.startOfDay(for: state.endDate)

        guard normalizedDate < normalizedStartDate || normalizedDate > normalizedEndDate else {
            return
        }

        let range = initialGeneratedDateRange(around: date)
        replaceGeneratedDateCells(from: range.startDate, to: range.endDate)
    }

    private func appendGeneratedDateCellsIfNeeded(visibleRange: CalendarVisibleIndexRange) {
        if visibleRange.startIndex < edgeGenerationThresholdDateCount {
            appendGeneratedDateCells(at: .start)
        }

        if loadedDateCount - visibleRange.endIndex < edgeGenerationThresholdDateCount {
            appendGeneratedDateCells(at: .end)
        }
    }

    private func appendGeneratedDateCells(at edge: CalendarState.LoadedEdge) {
        let nextStartDate: Date
        let nextEndDate: Date

        switch edge {
        case .start:
            let startKey = YearMonthKey(date: state.startDate, calendar: calendar)
                .addingMonths(generatedMonthBatchCount * -1, calendar: calendar)
            let endKey = YearMonthKey(date: state.startDate, calendar: calendar)
                .addingMonths(-1, calendar: calendar)
            let range = monthDateRange(from: startKey, to: endKey)
            nextStartDate = range.startDate
            nextEndDate = range.endDate
        case .end:
            let startKey = YearMonthKey(date: state.endDate, calendar: calendar)
                .addingMonths(1, calendar: calendar)
            let endKey = YearMonthKey(date: state.endDate, calendar: calendar)
                .addingMonths(generatedMonthBatchCount, calendar: calendar)
            let range = monthDateRange(from: startKey, to: endKey)
            nextStartDate = range.startDate
            nextEndDate = range.endDate
        }

        let finalRange = generatedDateRangeAfterAppending(
            edge: edge,
            nextStartDate: nextStartDate,
            nextEndDate: nextEndDate
        )

        if finalRange.shouldReplace {
            replaceGeneratedDateCells(
                from: finalRange.startDate,
                to: finalRange.endDate
            )
            return
        }

        let nextDaysByKey = makeDateCellItemsByDay(from: nextStartDate, to: nextEndDate)
        state = state.appended(
            startDate: nextStartDate,
            endDate: nextEndDate,
            daysByKey: nextDaysByKey,
            monthEventCache: monthEventCache
        )
    }

    private func generatedDateRangeAfterAppending(
        edge: CalendarState.LoadedEdge,
        nextStartDate: Date,
        nextEndDate: Date
    ) -> (startDate: Date, endDate: Date, shouldReplace: Bool) {
        let appendedStartDate = min(state.startDate, nextStartDate)
        let appendedEndDate = max(state.endDate, nextEndDate)

        guard generatedMonthCount(from: appendedStartDate, to: appendedEndDate) > maxGeneratedMonthCount else {
            return (
                startDate: appendedStartDate,
                endDate: appendedEndDate,
                shouldReplace: false
            )
        }

        switch edge {
        case .start:
            let startKey = YearMonthKey(date: appendedStartDate, calendar: calendar)
            let endKey = startKey.addingMonths(maxGeneratedMonthCount - 1, calendar: calendar)
            let range = monthDateRange(from: startKey, to: endKey)

            return (
                startDate: range.startDate,
                endDate: range.endDate,
                shouldReplace: true
            )

        case .end:
            let endKey = YearMonthKey(date: appendedEndDate, calendar: calendar)
            let startKey = endKey.addingMonths((maxGeneratedMonthCount - 1) * -1, calendar: calendar)
            let range = monthDateRange(from: startKey, to: endKey)

            return (
                startDate: range.startDate,
                endDate: range.endDate,
                shouldReplace: true
            )
        }
    }

    private func generatedMonthCount(from startDate: Date, to endDate: Date) -> Int {
        let startKey = YearMonthKey(date: startDate, calendar: calendar)
        let endKey = YearMonthKey(date: endDate, calendar: calendar)

        return ((endKey.year - startKey.year) * 12) + endKey.month - startKey.month + 1
    }

    private func prefetchReferenceMonthAndAdjacent(retryFailed: Bool) {
        requestMonths(
            adjacentMonthKeys(around: YearMonthKey(day: referenceDay)),
            retryFailed: retryFailed
        )
    }

    private func invalidateMonthEventCache() {
        monthEventCache.removeAll()
        pendingCreatedEventsByMonth.removeAll()
        state = state.replacingMonthEventCache(
            monthEventCache,
            updatingDateCells: makeDateCellItemsByDay(
                from: state.startDate,
                to: state.endDate,
                monthEventCache: monthEventCache
            )
        )
    }

    private func invalidateAndRefetchMonths(_ keys: Set<YearMonthKey>) {
        invalidateMonthEventCache(for: keys)
        requestMonths(Array(keys), retryFailed: false)
    }

    private func invalidateMonthEventCache(for keys: Set<YearMonthKey>) {
        keys.forEach { key in
            monthEventCache.removeValue(forKey: key)
            pendingCreatedEventsByMonth.removeValue(forKey: key)
        }

        let updatedDaysByKey = keys.reduce(into: [DayKey: CalendarDateCellItem]()) { result, key in
            result.merge(
                makeDateCellItemsForGeneratedMonth(key, monthEventCache: monthEventCache)
            ) { _, new in
                new
            }
        }

        state = state.replacingMonthEventCache(
            monthEventCache,
            updatingDateCells: updatedDaysByKey
        )
    }

    private func refetchDefaultPrefetchRange() {
        prefetchReferenceMonthAndAdjacent(retryFailed: false)
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
        switch monthEventCache[key] ?? .idle {
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
        monthEventCache[key] = entry
        let updatedDaysByKey: [DayKey: CalendarDateCellItem]

        if case .loaded = entry {
            updatedDaysByKey = makeDateCellItemsForGeneratedMonth(
                key,
                monthEventCache: monthEventCache
            )
        } else {
            updatedDaysByKey = [:]
        }

        guard !updatedDaysByKey.isEmpty || shouldPublishCacheOnlyChange(for: key) else {
            return
        }

        state = state.replacingMonthEventCache(
            monthEventCache,
            updatingDateCells: updatedDaysByKey
        )
    }

    private func setFailedMonthCacheEntry(
        _ failure: CalendarMonthEventFailure,
        for key: YearMonthKey
    ) {
        guard monthEventCache[key]?.isLoading == true else {
            return
        }

        setMonthCacheEntry(.failed(failure), for: key)
    }

    private func shouldPublishCacheOnlyChange(for key: YearMonthKey) -> Bool {
        key == YearMonthKey(day: referenceDay)
    }

    private func cachedEvents(
        in dateRange: ClosedRange<Date>,
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]
    ) -> [Event] {
        let cachedEvents = monthKeys(from: dateRange.lowerBound, to: dateRange.upperBound)
            .flatMap { key in
                monthEventCache[key]?.loadedEvents ?? []
            }
        let pendingEvents = pendingCreatedEventsByMonth.values.flatMap { $0 }

        return mergedSortedEvents(cachedEvents, with: pendingEvents).filter { event in
            dateRange.contains(event.startAt)
        }
    }

    private func makeDateCellItemsForGeneratedMonth(
        _ key: YearMonthKey,
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]
    ) -> [DayKey: CalendarDateCellItem] {
        guard let range = generatedDateRange(in: key) else {
            return [:]
        }

        return makeDateCellItemsByDay(
            from: range.startDate,
            to: range.endDate,
            monthEventCache: monthEventCache
        )
    }

    private func generatedDateRange(
        in key: YearMonthKey
    ) -> (startDate: Date, endDate: Date)? {
        let monthRange = key.dateRange(calendar: calendar)
        let startDate = max(monthRange.from, state.startDate)
        let endDate = min(monthRange.to, state.endDate)

        guard startDate <= endDate else {
            return nil
        }

        return (startDate: startDate, endDate: endDate)
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

        let visibleItems = Array(items[startIndex...endIndex])
        return visibleItems
    }

    private func makeTargetDayPreservingReferenceDay(year: Int, month: Int) -> DayKey {
        let clampedDay = min(referenceDay.day, lastDayOfMonth(year: year, month: month))
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

        switch monthEventCache[key] ?? .idle {
        case .loaded(let events):
            setMonthCacheEntry(.loaded(mergedSortedEvents(events, with: [event])), for: key)
        case .loading:
            pendingCreatedEventsByMonth[key] = mergedSortedEvents(
                pendingCreatedEventsByMonth[key] ?? [],
                with: [event]
            )
            refreshGeneratedDateCells(for: key)
        case .idle, .failed:
            pendingCreatedEventsByMonth[key] = mergedSortedEvents(
                pendingCreatedEventsByMonth[key] ?? [],
                with: [event]
            )
            refreshGeneratedDateCells(for: key)
        }
    }

    private func mergedSortedEvents(_ events: [Event], with additionalEvents: [Event]) -> [Event] {
        var eventsByID: [Int64: Event] = [:]

        events.forEach { event in
            eventsByID[event.id] = event
        }

        additionalEvents.forEach { event in
            eventsByID[event.id] = event
        }

        return sortedEvents(Array(eventsByID.values))
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

    private func monthKeys(from startDate: Date, to endDate: Date) -> [YearMonthKey] {
        let startKey = YearMonthKey(date: startDate, calendar: calendar)
        let endKey = YearMonthKey(date: endDate, calendar: calendar)
        let count = generatedMonthCount(from: startDate, to: endDate)

        return (0..<count).map { offset in
            startKey.addingMonths(offset, calendar: calendar)
        }.filter { key in
            key <= endKey
        }
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
        case .eventNotFound, .recurrenceEventNotFound, .recurrenceOccurrenceNotFound:
            self = .unexpected
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

enum CalendarEventMutationState: Equatable {
    case idle
    case saving
    case failed(CalendarEventMutationFailure)

    var isMutating: Bool {
        self == .saving
    }

    var failureMessage: String? {
        guard case .failed(let failure) = self else {
            return nil
        }

        return failure.message
    }
}

enum CalendarEventMutationFailure: Equatable {
    case eventNotFound
    case recurrenceEventNotFound
    case recurrenceOccurrenceNotFound
    case validationFailed
    case invalidTimeRange
    case network
    case unexpected

    init(error: EventServiceError) {
        switch error {
        case .eventNotFound:
            self = .eventNotFound
        case .recurrenceEventNotFound:
            self = .recurrenceEventNotFound
        case .recurrenceOccurrenceNotFound:
            self = .recurrenceOccurrenceNotFound
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
        case .eventNotFound:
            return "일정을 찾을 수 없습니다."
        case .recurrenceEventNotFound:
            return "반복 일정을 찾을 수 없습니다."
        case .recurrenceOccurrenceNotFound:
            return "반복 일정 항목을 찾을 수 없습니다."
        case .validationFailed:
            return "입력값을 확인해 주세요."
        case .invalidTimeRange:
            return "종료 시각은 시작 시각보다 늦어야 합니다."
        case .network:
            return "서버에 연결할 수 없습니다."
        case .unexpected:
            return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
    }
}
