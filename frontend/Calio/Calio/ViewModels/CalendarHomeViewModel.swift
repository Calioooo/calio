//
//  CalendarHomeViewModel.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation
import CoreGraphics

enum CalendarScrollSource: Equatable {
    case idle
    case strip
    case event
}

struct CalendarScrollTarget: Equatable {
    let id: UUID
    let offset: CGFloat
    let animated: Bool

    init(offset: CGFloat, animated: Bool = false) {
        self.id = UUID()
        self.offset = offset
        self.animated = animated
    }
}

struct CalendarVisibleIndexRange: Equatable {
    let startIndex: Int
    let endIndex: Int
}

struct CalendarPrependScrollCompensation: Equatable {
    let id: UUID
    let insertedCount: Int

    init(insertedCount: Int) {
        self.id = UUID()
        self.insertedCount = insertedCount
    }
}

enum CalendarScrollMetrics {
    static let stripVisibleCellCount = 7
    static let eventRowHeight: CGFloat = 111

    static func stripCellWidth(containerWidth: CGFloat) -> CGFloat {
        max(containerWidth / CGFloat(stripVisibleCellCount), 1)
    }

    static func progress(contentOffset: CGFloat, itemExtent: CGFloat) -> CGFloat {
        guard itemExtent > 0 else {
            return 0
        }

        return max(0, contentOffset / itemExtent)
    }

    static func targetOffset(progress: CGFloat, itemExtent: CGFloat) -> CGFloat {
        max(0, progress) * max(itemExtent, 0)
    }

    static func nearestIndex(progress: CGFloat, itemCount: Int) -> Int? {
        guard itemCount > 0 else {
            return nil
        }

        let roundedIndex = Int(progress.rounded())
        return min(max(roundedIndex, 0), itemCount - 1)
    }

    static func visibleIndexRange(
        contentOffset: CGFloat,
        viewportLength: CGFloat,
        itemExtent: CGFloat,
        itemCount: Int
    ) -> CalendarVisibleIndexRange? {
        guard itemCount > 0, itemExtent > 0 else {
            return nil
        }

        let normalizedOffset = max(contentOffset, 0)
        let visibleStartIndex = Int(floor(normalizedOffset / itemExtent))
        let rawEndIndex = Int(ceil((normalizedOffset + max(viewportLength, 0)) / itemExtent)) - 1
        let visibleEndIndex = max(visibleStartIndex, rawEndIndex)

        return CalendarVisibleIndexRange(
            startIndex: min(max(visibleStartIndex, 0), itemCount - 1),
            endIndex: min(max(visibleEndIndex, 0), itemCount - 1)
        )
    }
}

@MainActor
final class CalendarHomeViewModel: ObservableObject {
    @Published private(set) var state: CalendarState
    @Published private(set) var prependScrollCompensation: CalendarPrependScrollCompensation?

    private let initialLoadPastDays = 60
    private let initialLoadFutureDays = 60

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

        state = state.focused(on: day)
    }

    func index(of day: DayKey) -> Int? {
        loadedDateCellItems.firstIndex { item in
            item.id == day
        }
    }

    func day(at index: Int) -> DayKey? {
        let items = loadedDateCellItems
        guard items.indices.contains(index) else {
            return nil
        }

        return items[index].id
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
                let insertedDayCount = edge == .start
                    ? daysByKey.keys.filter { self.state.daysByKey[$0] == nil }.count
                    : 0

                self.state = self.state.appended(startDate: loadStartDate, endDate: loadEndDate, daysByKey: daysByKey)

                if insertedDayCount > 0 {
                    self.prependScrollCompensation = CalendarPrependScrollCompensation(insertedCount: insertedDayCount)
                }
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
