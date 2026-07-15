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
    @Published private(set) var tags: [CalendarTag] = []
    @Published private(set) var tagMutationState: CalendarTagMutationState = .idle
    
    private let initialLoadedPastMonths = 3
    private let initialLoadedFutureMonths = 3
    private let loadedEdgeThresholdDayCount = 20
    private let loadedMonthBatchCount = 2
    private let maxLoadedMonthCount = 18
    
    private let dateService: CalendarDateService
    private let eventService: EventService
    private let tagService: TagService
    private let nationalHolidayService: NationalHolidayService
    private let calendar: Calendar
    private var lastVisibleMonthKeys: Set<YearMonthKey> = []
    private var lastHandledVisibleRange: CalendarVisibleIndexRange?
    private var monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]
    private var monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry]
    private var pendingCreatedEventsByMonth: [YearMonthKey: [Event]] = [:]
    private var isLoadingTags = false
    
    init(
        calendar: Calendar = .current,
        dateService: CalendarDateService = CalendarDateService(),
        eventService: EventService = EventService(),
        tagService: TagService = TagService(),
        nationalHolidayService: NationalHolidayService = NationalHolidayService(),
        initialState: CalendarState? = nil,
        initialReferenceDate: Date = Date()
    ) {
        self.dateService = dateService
        self.calendar = calendar
        self.eventService = eventService
        self.tagService = tagService
        self.nationalHolidayService = nationalHolidayService
        self.monthEventCache = initialState?.monthEventCache ?? [:]
        self.monthHolidayCache = initialState?.monthHolidayCache ?? [:]
        let referenceDate = initialState?.startDate ?? initialReferenceDate
        self.referenceDay = DayKey(date: referenceDate, calendar: calendar)
        
        self.state = initialState ?? CalendarState(
            startDate: initialReferenceDate,
            endDate: initialReferenceDate,
            daysByKey: [:]
        )
    }
    
    var loadedDateCellItems: [CalendarDayItem] {
        return state.loadedDateCellItems(calendar: calendar)
    }
    
    var loadedDateCount: Int {
        state.daysByKey.count
    }

    var isReferenceDayToday: Bool {
        dateService.isToday(referenceDay.toDate(calendar: calendar))
    }

    var eventLoadState: CalendarEventLoadState {
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
        guard state.daysByKey.isEmpty && monthEventCache.isEmpty && monthHolidayCache.isEmpty else { return }

        let referenceDate = referenceDay.toDate(calendar: calendar)
        let range = initialLoadedDateRange(around: referenceDate)
        replaceLoadedDateCells(from: range.startDate, to: range.endDate)
        prefetchReferenceMonthAndAdjacent(retryFailed: false)
    }

    func loadTagsIfNeeded() {
        guard tags.isEmpty && !isLoadingTags else {
            return
        }

        isLoadingTags = true

        Task {
            defer {
                isLoadingTags = false
            }

            do {
                tags = try await tagService.fetchTags()
            } catch {
                tags = [CalendarTag.fallback]
            }
        }
    }

    private func reloadTags() async {
        do {
            tags = try await tagService.fetchTags()
        } catch {
            tags = tags.isEmpty ? [CalendarTag.fallback] : tags
        }
    }
    
    func setReferenceDay(_ day: DayKey) {
        guard referenceDay != day else {
            return
        }
        
        referenceDay = day
        ensureLoadedDateCells(containing: day)
        requestHolidayMonth(YearMonthKey(day: day))
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
        appendLoadedDateCellsIfNeeded(visibleRange: visibleRange)
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

    func moveMonthToFirstDay(by value: Int) {
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

        selectMonthFirstDay(year: year, month: month)
    }

    func selectYearMonth(year: Int, month: Int) {
        let targetDay = makeTargetDayPreservingReferenceDay(year: year, month: month)
        referenceDay = targetDay
        ensureLoadedDateCells(containing: targetDay)
        prefetchReferenceMonthAndAdjacent(retryFailed: true)
    }

    func selectMonthFirstDay(year: Int, month: Int) {
        let targetDay = DayKey(year: year, month: month, day: 1)
        referenceDay = targetDay
        ensureLoadedDateCells(containing: targetDay)
        prefetchReferenceMonthAndAdjacent(retryFailed: true)
    }

    func moveToToday() {
        let today = DayKey(date: Date(), calendar: calendar)
        referenceDay = today
        ensureLoadedDateCells(containing: today)
        prefetchReferenceMonthAndAdjacent(retryFailed: true)
    }

    func retryEventLoading() {
        requestMonths([YearMonthKey(day: referenceDay)], retryFailed: true)
    }

    func resetCreateState() {
        createState = .idle
    }

    func resetMutationState() {
        mutationState = .idle
    }

    func resetTagMutationState() {
        guard tagMutationState != .idle else {
            return
        }

        tagMutationState = .idle
    }

    func createCustomTag(_ input: CustomTagInput) async -> Bool {
        guard !tagMutationState.isMutating else {
            return false
        }

        tagMutationState = .saving

        do {
            _ = try await tagService.createCustomTag(input)
            await reloadTags()
            tagMutationState = .idle
            return true
        } catch let error as EventServiceError {
            tagMutationState = .failed(CalendarTagMutationFailure(error: error))
            return false
        } catch {
            tagMutationState = .failed(.unexpected)
            return false
        }
    }

    func updateCustomTag(_ tag: CalendarTag, input: CustomTagInput) async -> Bool {
        guard !tagMutationState.isMutating,
              tag.tagType == .custom
        else {
            return false
        }

        tagMutationState = .saving

        do {
            _ = try await tagService.updateCustomTag(tagId: tag.id, input: input)
            await reloadTags()
            invalidateMonthEventCache()
            refetchDefaultPrefetchRange()
            tagMutationState = .idle
            return true
        } catch let error as EventServiceError {
            tagMutationState = .failed(CalendarTagMutationFailure(error: error))
            return false
        } catch {
            tagMutationState = .failed(.unexpected)
            return false
        }
    }

    func deleteCustomTag(_ tag: CalendarTag) async -> Bool {
        guard !tagMutationState.isMutating,
              tag.tagType == .custom
        else {
            return false
        }

        tagMutationState = .saving

        do {
            try await tagService.deleteCustomTag(tagId: tag.id)
            await reloadTags()
            invalidateMonthEventCache()
            refetchDefaultPrefetchRange()
            tagMutationState = .idle
            return true
        } catch let error as EventServiceError {
            tagMutationState = .failed(CalendarTagMutationFailure(error: error))
            return false
        } catch {
            tagMutationState = .failed(.unexpected)
            return false
        }
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
        guard let eventId = event.backendId else {
            return false
        }

        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            let updatedEvent = try await eventService.updateEvent(eventId: eventId, input: input)
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
        guard let recurrenceId = event.recurrenceId,
              let originStartAt = event.originStartAt else {
            return false
        }

        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            _ = try await eventService.updateRecurrenceOccurrence(
                recurrenceId: recurrenceId,
                originStartAt: originStartAt,
                input: RecurrenceOccurrenceUpdateInput(
                    startAt: input.startAt,
                    endAt: input.endAt,
                    isAllDay: input.isAllDay
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
                    recurrenceStartDate: input.recurrenceStartDate,
                    recurrenceEndDate: input.recurrenceEndDate,
                    recurrenceStartTime: input.recurrenceStartTime,
                    recurrenceEndTime: input.recurrenceEndTime,
                    recurrenceFrequency: input.recurrenceFrequency,
                    isAllDay: input.isAllDay,
                    tagId: input.tagId
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
        guard let eventId = event.backendId else {
            return false
        }

        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            try await eventService.deleteEvent(eventId: eventId)
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
        guard let recurrenceId = event.recurrenceId,
              let originStartAt = event.originStartAt else {
            return false
        }

        guard !mutationState.isMutating else {
            return false
        }

        mutationState = .saving

        do {
            try await eventService.deleteRecurrenceOccurrence(
                recurrenceId: recurrenceId,
                originStartAt: originStartAt
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
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]? = nil,
        monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry]? = nil
    ) -> [DayKey: CalendarDayItem] {
        let eventsByDay = makeEventsByDay(
            cachedEvents(
                in: startDate...endDate,
                monthEventCache: monthEventCache ?? self.monthEventCache
            )
        )
        let holidaysByDay = makeHolidaysByDay(
            cachedHolidays(
                monthHolidayCache: monthHolidayCache ?? self.monthHolidayCache
            )
        )
        
        return Dictionary(
            uniqueKeysWithValues: makeDates(from: startDate, to: endDate).map { date in
                let day = DayKey(date: date, calendar: calendar)
                
                return (
                    day,
                    CalendarDayItem(
                        id: day,
                        weekday: dateService.getWeekday(from: date),
                        monthText: dateService.monthText(from: date),
                        dayText: dateService.dayText(from: date),
                        isToday: dateService.isToday(date),
                        events: eventsByDay[day] ?? [],
                        holidays: holidaysByDay[day] ?? []
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

    private func fetchHolidayMonth(_ key: YearMonthKey) {
        Task {
            do {
                let holidays = try await nationalHolidayService.fetchNationalHolidays(for: key)
                self.setMonthHolidayCacheEntry(.loaded(holidays), for: key)
            } catch let error as NationalHolidayServiceError {
                self.setFailedMonthHolidayCacheEntry(CalendarMonthHolidayFailure(error: error), for: key)
            } catch {
                self.setFailedMonthHolidayCacheEntry(.unexpected, for: key)
            }
        }
    }
    
    private func makeEventsByDay(_ events: [Event]) -> [DayKey: [Event]] {
        events.reduce(into: [DayKey: [Event]]()) { eventsByDay, event in
            for day in daysOverlapping(event) {
                eventsByDay[day, default: []].append(event)
            }
        }
    }

    private func makeHolidaysByDay(_ holidays: [NationalHoliday]) -> [DayKey: [NationalHoliday]] {
        holidays.reduce(into: [DayKey: [NationalHoliday]]()) { holidaysByDay, holiday in
            holidaysByDay[holiday.day, default: []].append(holiday)
        }
    }
    
    private func daysOverlapping(_ event: Event) -> [DayKey] {
        let firstDayDate = calendar.startOfDay(for: event.startAt)
        let lastIncludedDate = lastIncludedDate(for: event)
        
        guard firstDayDate <= lastIncludedDate else {
            return []
        }
        
        return sequence(first: firstDayDate) { currentDate in
            self.calendar.date(byAdding: .day, value: 1, to: currentDate)
        }
        .prefix { date in
            date <= lastIncludedDate
        }
        .map { date in
            DayKey(date: date, calendar: calendar)
        }
    }
    
    private func lastIncludedDate(for event: Event) -> Date {
        let endDayStart = calendar.startOfDay(for: event.endAt)
        
        if event.endAt == endDayStart,
           let previousDay = calendar.date(byAdding: .day, value: -1, to: endDayStart) {
            return previousDay
        }
        
        return endDayStart
    }

    private func replaceLoadedDateCells(from startDate: Date, to endDate: Date) {
        state = state.replacingDateCells(
            startDate: startDate,
            endDate: endDate,
            daysByKey: makeDateCellItemsByDay(from: startDate, to: endDate),
            monthEventCache: monthEventCache,
            monthHolidayCache: monthHolidayCache
        )
    }

    private func refreshLoadedDateCells(for key: YearMonthKey) {
        let daysByKey = makeDateCellItemsForLoadedMonth(
            key,
            monthEventCache: monthEventCache,
            monthHolidayCache: monthHolidayCache
        )

        guard !daysByKey.isEmpty else {
            return
        }

        state = state.replacingMonthEventCache(
            monthEventCache,
            updatingDateCells: daysByKey
        )
    }

    private func initialLoadedDateRange(around date: Date) -> (startDate: Date, endDate: Date) {
        let referenceMonthKey = YearMonthKey(date: date, calendar: calendar)
        let startKey = referenceMonthKey.addingMonths(
            initialLoadedPastMonths * -1,
            calendar: calendar
        )
        let endKey = referenceMonthKey.addingMonths(
            initialLoadedFutureMonths,
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

    private func ensureLoadedDateCells(containing day: DayKey) {
        let date = day.toDate(calendar: calendar)
        let normalizedDate = calendar.startOfDay(for: date)
        let normalizedStartDate = calendar.startOfDay(for: state.startDate)
        let normalizedEndDate = calendar.startOfDay(for: state.endDate)

        guard normalizedDate < normalizedStartDate || normalizedDate > normalizedEndDate else {
            return
        }

        let range = initialLoadedDateRange(around: date)
        replaceLoadedDateCells(from: range.startDate, to: range.endDate)
    }

    private func appendLoadedDateCellsIfNeeded(visibleRange: CalendarVisibleIndexRange) {
        if visibleRange.startIndex < loadedEdgeThresholdDayCount {
            appendLoadedDateCells(at: .start)
        }

        if loadedDateCount - visibleRange.endIndex < loadedEdgeThresholdDayCount {
            appendLoadedDateCells(at: .end)
        }
    }

    private func appendLoadedDateCells(at edge: CalendarState.LoadedEdge) {
        let nextStartDate: Date
        let nextEndDate: Date

        switch edge {
        case .start:
            let startKey = YearMonthKey(date: state.startDate, calendar: calendar)
                .addingMonths(loadedMonthBatchCount * -1, calendar: calendar)
            let endKey = YearMonthKey(date: state.startDate, calendar: calendar)
                .addingMonths(-1, calendar: calendar)
            let range = monthDateRange(from: startKey, to: endKey)
            nextStartDate = range.startDate
            nextEndDate = range.endDate
        case .end:
            let startKey = YearMonthKey(date: state.endDate, calendar: calendar)
                .addingMonths(1, calendar: calendar)
            let endKey = YearMonthKey(date: state.endDate, calendar: calendar)
                .addingMonths(loadedMonthBatchCount, calendar: calendar)
            let range = monthDateRange(from: startKey, to: endKey)
            nextStartDate = range.startDate
            nextEndDate = range.endDate
        }

        let finalRange = loadedDateRangeAfterAppending(
            edge: edge,
            nextStartDate: nextStartDate,
            nextEndDate: nextEndDate
        )

        if finalRange.shouldReplace {
            replaceLoadedDateCells(
                from: finalRange.startDate,
                to: finalRange.endDate
            )
            requestHolidayMonths(monthKeys(from: finalRange.startDate, to: finalRange.endDate))
            return
        }

        let nextDaysByKey = makeDateCellItemsByDay(from: nextStartDate, to: nextEndDate)
        state = state.appended(
            startDate: nextStartDate,
            endDate: nextEndDate,
            daysByKey: nextDaysByKey,
            monthEventCache: monthEventCache,
            monthHolidayCache: monthHolidayCache
        )
        requestHolidayMonths(monthKeys(from: nextStartDate, to: nextEndDate))
    }

    private func loadedDateRangeAfterAppending(
        edge: CalendarState.LoadedEdge,
        nextStartDate: Date,
        nextEndDate: Date
    ) -> (startDate: Date, endDate: Date, shouldReplace: Bool) {
        let appendedStartDate = min(state.startDate, nextStartDate)
        let appendedEndDate = max(state.endDate, nextEndDate)

        guard loadedMonthCount(from: appendedStartDate, to: appendedEndDate) > maxLoadedMonthCount else {
            return (
                startDate: appendedStartDate,
                endDate: appendedEndDate,
                shouldReplace: false
            )
        }

        switch edge {
        case .start:
            let startKey = YearMonthKey(date: appendedStartDate, calendar: calendar)
            let endKey = startKey.addingMonths(maxLoadedMonthCount - 1, calendar: calendar)
            let range = monthDateRange(from: startKey, to: endKey)

            return (
                startDate: range.startDate,
                endDate: range.endDate,
                shouldReplace: true
            )

        case .end:
            let endKey = YearMonthKey(date: appendedEndDate, calendar: calendar)
            let startKey = endKey.addingMonths((maxLoadedMonthCount - 1) * -1, calendar: calendar)
            let range = monthDateRange(from: startKey, to: endKey)

            return (
                startDate: range.startDate,
                endDate: range.endDate,
                shouldReplace: true
            )
        }
    }

    private func loadedMonthCount(from startDate: Date, to endDate: Date) -> Int {
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
                monthEventCache: monthEventCache,
                monthHolidayCache: monthHolidayCache
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

        let updatedDaysByKey = keys.reduce(into: [DayKey: CalendarDayItem]()) { result, key in
            result.merge(
                makeDateCellItemsForLoadedMonth(
                    key,
                    monthEventCache: monthEventCache,
                    monthHolidayCache: monthHolidayCache
                )
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

    private func prefetchMonthsForVisibleItemsIfNeeded(_ visibleItems: [CalendarDayItem]) {
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
            requestEventMonth(key, retryFailed: retryFailed)
            requestHolidayMonth(key)
        }
    }

    private func requestEventMonth(_ key: YearMonthKey, retryFailed: Bool) {
        guard shouldFetchMonth(key, retryFailed: retryFailed) else {
            return
        }

        setMonthCacheEntry(.loading, for: key)
        fetchMonth(key)
    }

    private func requestHolidayMonth(_ key: YearMonthKey) {
        guard shouldFetchHolidayMonth(key) else {
            return
        }

        setMonthHolidayCacheEntry(.loading, for: key)
        fetchHolidayMonth(key)
    }

    private func requestHolidayMonths(_ keys: [YearMonthKey]) {
        Set(keys).sorted().forEach(requestHolidayMonth(_:))
    }

    private func shouldFetchHolidayMonth(_ key: YearMonthKey) -> Bool {
        switch monthHolidayCache[key] ?? .idle {
        case .idle, .failed:
            return true
        case .loading, .loaded:
            return false
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
        let updatedDaysByKey: [DayKey: CalendarDayItem]

        if case .loaded = entry {
            updatedDaysByKey = makeDateCellItemsForLoadedMonth(
                key,
                monthEventCache: monthEventCache,
                monthHolidayCache: monthHolidayCache
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

    private func setMonthHolidayCacheEntry(
        _ entry: CalendarMonthHolidayCacheEntry,
        for key: YearMonthKey
    ) {
        monthHolidayCache[key] = entry
        let updatedDaysByKey: [DayKey: CalendarDayItem]

        switch entry {
        case .loaded, .failed:
            updatedDaysByKey = makeDateCellItemsForLoadedMonth(
                key,
                monthEventCache: monthEventCache,
                monthHolidayCache: monthHolidayCache
            )
        case .idle, .loading:
            updatedDaysByKey = [:]
        }

        guard !updatedDaysByKey.isEmpty || shouldPublishCacheOnlyChange(for: key) else {
            return
        }

        state = state.replacingMonthHolidayCache(
            monthHolidayCache,
            updatingDateCells: updatedDaysByKey
        )
    }

    private func setFailedMonthHolidayCacheEntry(
        _ failure: CalendarMonthHolidayFailure,
        for key: YearMonthKey
    ) {
        guard monthHolidayCache[key]?.isLoading == true else {
            return
        }

        setMonthHolidayCacheEntry(.failed(failure), for: key)
    }

    private func shouldPublishCacheOnlyChange(for key: YearMonthKey) -> Bool {
        key == YearMonthKey(day: referenceDay)
    }

    private func cachedEvents(
        in dateRange: ClosedRange<Date>,
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry]
    ) -> [Event] {
        let cachedEvents = monthEventCache.values.flatMap(\.loadedEvents)
        let pendingEvents = pendingCreatedEventsByMonth.values.flatMap { $0 }

        return mergedSortedEvents(cachedEvents, with: pendingEvents).filter { event in
            event.startAt <= dateRange.upperBound && event.endAt > dateRange.lowerBound
        }
    }

    private func cachedHolidays(
        monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry]
    ) -> [NationalHoliday] {
        monthHolidayCache.values.flatMap(\.loadedHolidays)
    }

    private func makeDateCellItemsForLoadedMonth(
        _ key: YearMonthKey,
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry],
        monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry]
    ) -> [DayKey: CalendarDayItem] {
        guard let range = loadedDateRange(in: key) else {
            return [:]
        }

        return makeDateCellItemsByDay(
            from: range.startDate,
            to: range.endDate,
            monthEventCache: monthEventCache,
            monthHolidayCache: monthHolidayCache
        )
    }

    private func loadedDateRange(
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

    private func loadedItems(in visibleRange: CalendarVisibleIndexRange) -> [CalendarDayItem] {
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
            refreshLoadedDateCells(for: key)
        case .idle, .failed:
            pendingCreatedEventsByMonth[key] = mergedSortedEvents(
                pendingCreatedEventsByMonth[key] ?? [],
                with: [event]
            )
            refreshLoadedDateCells(for: key)
        }
    }

    private func mergedSortedEvents(_ events: [Event], with additionalEvents: [Event]) -> [Event] {
        var eventsByID: [String: Event] = [:]

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
        let count = loadedMonthCount(from: startDate, to: endDate)

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

enum CalendarTagMutationState: Equatable {
    case idle
    case saving
    case failed(CalendarTagMutationFailure)

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

enum CalendarTagMutationFailure: Equatable {
    case validationFailed
    case network
    case unexpected

    init(error: EventServiceError) {
        switch error {
        case .validationFailed, .invalidTimeRange:
            self = .validationFailed
        case .network:
            self = .network
        case .eventNotFound, .recurrenceEventNotFound, .recurrenceOccurrenceNotFound, .decoding, .unexpected:
            self = .unexpected
        }
    }

    var message: String {
        switch self {
        case .validationFailed:
            return "태그 이름과 색상을 확인해 주세요."
        case .network:
            return "서버에 연결할 수 없습니다."
        case .unexpected:
            return "태그를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
    }
}
