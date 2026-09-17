import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct CalendarStateTests {

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
            daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
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
            ]
        )

        let loadedItems = state.loadedDateCellItems(calendar: calendar)

        #expect(loadedItems.count == 3)
        #expect(loadedItems[1].id == emptyDayItem.id)
        #expect(loadedItems[1].events.isEmpty)
    }

    @Test func calendarStateDoesNotOwnReferenceDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let item = makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar)
        let state = CalendarState(
            startDate: baseDate,
            endDate: baseDate,
            daysByKey: [item.id: item]
        )

        #expect(state.loadedDateCellItems(calendar: calendar).map(\.id) == [item.id])
    }
}
