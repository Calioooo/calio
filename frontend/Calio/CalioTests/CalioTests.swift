//
//  CalioTests.swift
//  CalioTests
//
//  Created by 김준하 on 6/6/26.
//

import Testing
import Foundation
@testable import Calio

struct CalioTests {

    @Test func calendarDisplayModeStartsFromWeek() async throws {
        let displayMode = CalendarDisplayMode.week

        #expect(displayMode == .week)
    }

    @Test func drawerDragDownPastThresholdResolvesToMonth() async throws {
        let displayMode = CalendarDisplayMode.week

        #expect(displayMode.resolved(afterDragTranslationHeight: 41) == .month)
    }

    @Test func drawerDragUpPastThresholdResolvesToWeek() async throws {
        let displayMode = CalendarDisplayMode.month

        #expect(displayMode.resolved(afterDragTranslationHeight: -41) == .week)
    }

    @Test func drawerDragInsideThresholdKeepsCurrentDisplayMode() async throws {
        #expect(CalendarDisplayMode.week.resolved(afterDragTranslationHeight: 40) == .week)
        #expect(CalendarDisplayMode.month.resolved(afterDragTranslationHeight: -40) == .month)
    }

    @Test func scheduleDrawerUsesItemsAndCallbacksWithoutViewModel() async throws {
        let drawer = CalendarScheduleDrawerView(
            items: [],
            focusedDay: DayKey(date: Date()),
            displayMode: .week,
            onFocusedDayChanged: { _ in },
            onDragEnded: { _ in }
        )

        #expect(drawer.items.isEmpty)
        #expect(drawer.displayMode == .week)
    }
    
    @Test func calendarStateReturnsLoadedDateItemsInDateOrder() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let items = [
            makeDateCellItem(dayOffset: 2, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        ]
        let state = CalendarState(
            startDate: baseDate,
            endDate: try #require(calendar.date(byAdding: .day, value: 2, to: baseDate)),
            daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) }),
            focusedDay: items[1].id
        )
        
        let loadedDays = state.loadedDateCellItems(calendar: calendar).map(\.id)
        
        #expect(loadedDays == [
            makeDayKey(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDayKey(dayOffset: 1, from: baseDate, calendar: calendar),
            makeDayKey(dayOffset: 2, from: baseDate, calendar: calendar)
        ])
    }
    
    @Test func calendarStatePreservesEmptyDaysInsideLoadedRange() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let emptyDayItem = makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        let state = CalendarState(
            startDate: baseDate,
            endDate: try #require(calendar.date(byAdding: .day, value: 2, to: baseDate)),
            daysByKey: [
                makeDayKey(dayOffset: 0, from: baseDate, calendar: calendar): makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar, events: [makeEvent(on: baseDate)]),
                emptyDayItem.id: emptyDayItem,
                makeDayKey(dayOffset: 2, from: baseDate, calendar: calendar): makeDateCellItem(dayOffset: 2, from: baseDate, calendar: calendar)
            ],
            focusedDay: emptyDayItem.id
        )
        
        let loadedItems = state.loadedDateCellItems(calendar: calendar)
        
        #expect(loadedItems.count == 3)
        #expect(loadedItems[1].id == emptyDayItem.id)
        #expect(loadedItems[1].events.isEmpty)
    }
    
    @Test func calendarStateFocusesOnlyCanonicalFocusedDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let firstDay = makeDayKey(dayOffset: 0, from: baseDate, calendar: calendar)
        let secondDay = makeDayKey(dayOffset: 1, from: baseDate, calendar: calendar)
        let state = CalendarState(
            startDate: baseDate,
            endDate: try #require(calendar.date(byAdding: .day, value: 1, to: baseDate)),
            daysByKey: [
                firstDay: makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar),
                secondDay: makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
            ],
            focusedDay: firstDay
        )
        
        #expect(state.focused(on: secondDay).focusedDay == secondDay)
    }
    
    @Test func calendarStateDetectsLoadedRangeEdgesByThreshold() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let endDate = try #require(calendar.date(byAdding: .day, value: 10, to: baseDate))
        let state = CalendarState(
            startDate: baseDate,
            endDate: endDate,
            daysByKey: [:],
            focusedDay: makeDayKey(dayOffset: 5, from: baseDate, calendar: calendar)
        )
        
        #expect(state.nearLoadedEdge(around: makeDayKey(dayOffset: 1, from: baseDate, calendar: calendar), thresholdDays: 3, calendar: calendar) == .start)
        #expect(state.nearLoadedEdge(around: makeDayKey(dayOffset: 9, from: baseDate, calendar: calendar), thresholdDays: 3, calendar: calendar) == .end)
        #expect(state.nearLoadedEdge(around: makeDayKey(dayOffset: 5, from: baseDate, calendar: calendar), thresholdDays: 3, calendar: calendar) == nil)
    }
    
    @Test func scrollingDateViewsReceiveItemsFocusedDayAndCallbacksWithoutViewModel() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let items = [
            makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        ]
        
        let strip = CalendarDateStripView(
            items: items,
            focusedDay: items[0].id,
            onFocusedDayChanged: { _ in }
        )
        let eventList = CalendarDateEventView(
            items: items,
            focusedDay: items[0].id,
            onFocusedDayChanged: { _ in }
        )
        
        #expect(strip.items.count == 2)
        #expect(strip.focusedDay == items[0].id)
        #expect(eventList.items.count == 2)
        #expect(eventList.focusedDay == items[0].id)
    }

    @MainActor
    @Test func calendarHomeViewModelFocusDayUpdatesCanonicalFocusedDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let focusedDay = makeDayKey(dayOffset: 3, from: baseDate, calendar: calendar)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            initialState: makeLoadedState(dayOffsets: 0...10, focusedOffset: 0, from: baseDate, calendar: calendar)
        )

        viewModel.focusDay(focusedDay)

        #expect(viewModel.state.focusedDay == focusedDay)
    }

    @MainActor
    @Test func calendarHomeViewModelRequestsPastAndFutureLoadsNearLoadedRangeEdges() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: makeLoadedState(dayOffsets: 0...60, focusedOffset: 30, from: baseDate, calendar: calendar)
        )

        viewModel.focusDay(makeDayKey(dayOffset: 1, from: baseDate, calendar: calendar))
        viewModel.focusDay(makeDayKey(dayOffset: 59, from: baseDate, calendar: calendar))
        await repository.waitForRequestCount(2)

        #expect(repository.requestCount == 2)
        #expect(repository.requestDirections(relativeTo: baseDate, calendar: calendar) == [.past, .future])
    }

    @MainActor
    @Test func calendarHomeViewModelPreventsDuplicateSameDirectionLoadsWhileLoading() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(shouldSuspend: true)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: makeLoadedState(dayOffsets: 0...60, focusedOffset: 30, from: baseDate, calendar: calendar)
        )

        viewModel.focusDay(makeDayKey(dayOffset: 59, from: baseDate, calendar: calendar))
        await repository.waitForRequestCount(1)
        viewModel.focusDay(makeDayKey(dayOffset: 58, from: baseDate, calendar: calendar))
        await Task.yield()

        #expect(repository.requestCount == 1)
        repository.finishSuspendedRequests()
    }

    @MainActor
    @Test func calendarHomeViewModelKeepsRangeAndFocusWhenAdditionalLoadFails() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(error: TestEventRepositoryError.failed)
        let initialState = makeLoadedState(dayOffsets: 0...60, focusedOffset: 30, from: baseDate, calendar: calendar)
        let focusedDay = makeDayKey(dayOffset: 59, from: baseDate, calendar: calendar)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: initialState
        )

        viewModel.focusDay(focusedDay)
        await repository.waitForRequestCount(1)
        await Task.yield()

        #expect(viewModel.state.startDate == initialState.startDate)
        #expect(viewModel.state.endDate == initialState.endDate)
        #expect(viewModel.state.daysByKey.count == initialState.daysByKey.count)
        #expect(viewModel.state.focusedDay == focusedDay)
    }
    
    private var fixedCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }
    
    private func makeDayKey(dayOffset: Int, from baseDate: Date, calendar: Calendar) -> DayKey {
        let date = calendar.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate
        return DayKey(date: date, calendar: calendar)
    }
    
    private func makeDateCellItem(
        dayOffset: Int,
        from baseDate: Date,
        calendar: Calendar,
        events: [Event] = []
    ) -> CalendarDateCellItem {
        let date = calendar.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate
        let dateService = CalendarDateService(calendar: calendar)
        
        return CalendarDateCellItem(
            id: DayKey(date: date, calendar: calendar),
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: false,
            isSelected: false,
            events: events
        )
    }

    private func makeLoadedState(
        dayOffsets: ClosedRange<Int>,
        focusedOffset: Int,
        from baseDate: Date,
        calendar: Calendar
    ) -> CalendarState {
        let items = dayOffsets.map { offset in
            makeDateCellItem(dayOffset: offset, from: baseDate, calendar: calendar)
        }
        let startDate = calendar.date(byAdding: .day, value: dayOffsets.lowerBound, to: baseDate) ?? baseDate
        let endDate = calendar.date(byAdding: .day, value: dayOffsets.upperBound, to: baseDate) ?? baseDate

        return CalendarState(
            startDate: startDate,
            endDate: endDate,
            daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) }),
            focusedDay: makeDayKey(dayOffset: focusedOffset, from: baseDate, calendar: calendar)
        )
    }
    
    private func makeEvent(on date: Date) -> Event {
        Event(
            id: 1,
            title: "테스트 일정",
            description: "",
            startAt: date,
            endAt: date.addingTimeInterval(3600),
            colorCode: "#4F46E5"
        )
    }

}

private enum TestEventRepositoryError: Error {
    case failed
}

private enum RequestedLoadDirection: Equatable {
    case past
    case future
}

private final class RecordingEventRepository: EventRepository {
    private(set) var requests: [(startDate: Date, endDate: Date)] = []
    private var waiters: [(Int, CheckedContinuation<Void, Never>)] = []
    private var suspendedContinuations: [CheckedContinuation<[EventResponseDTO], Error>] = []
    private let shouldSuspend: Bool
    private let error: Error?

    init(shouldSuspend: Bool = false, error: Error? = nil) {
        self.shouldSuspend = shouldSuspend
        self.error = error
    }

    var requestCount: Int {
        requests.count
    }

    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO] {
        requests.append((startDate, endDate))
        resumeReadyWaiters()

        if let error {
            throw error
        }

        if shouldSuspend {
            return try await withCheckedThrowingContinuation { continuation in
                suspendedContinuations.append(continuation)
            }
        }

        return []
    }

    func waitForRequestCount(_ count: Int) async {
        guard requests.count < count else {
            return
        }

        await withCheckedContinuation { continuation in
            waiters.append((count, continuation))
        }
    }

    func finishSuspendedRequests() {
        let continuations = suspendedContinuations
        suspendedContinuations.removeAll()
        continuations.forEach { continuation in
            continuation.resume(returning: [])
        }
    }

    func requestDirections(relativeTo baseDate: Date, calendar: Calendar) -> [RequestedLoadDirection] {
        requests.map { request in
            request.startDate < baseDate ? .past : .future
        }
    }

    private func resumeReadyWaiters() {
        let readyWaiters = waiters.filter { count, _ in
            requests.count >= count
        }
        waiters.removeAll { count, _ in
            requests.count >= count
        }
        readyWaiters.forEach { _, continuation in
            continuation.resume()
        }
    }
}
