//
//  CalioTests.swift
//  CalioTests
//
//  Created by 김준하 on 6/6/26.
//

import Testing
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
            displayMode: .week,
            onSelectedEvent: { _ in },
            onDragEnded: { _ in }
        )

        #expect(drawer.items.isEmpty)
        #expect(drawer.displayMode == .week)
    }

}
