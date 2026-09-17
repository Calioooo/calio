import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct CalendarDisplayTests {

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

    @MainActor
    @Test func scheduleDrawerUsesItemsAndCallbacksWithoutViewModel() async throws {
        let drawer = CalendarScheduleDrawerView(
            items: [],
            referenceDay: DayKey(date: Date()),
            displayMode: .week,
            eventLoadState: .idle,
            onReferenceDayChanged: { _ in },
            onVisibleRangeChanged: { _ in },
            onRetryEventLoading: {},
            onDragEnded: { _ in }
        )

        #expect(drawer.items.isEmpty)
        #expect(drawer.displayMode == .week)
    }
}
