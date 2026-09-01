import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct CalendarScrollFocusCoordinatorTests {

    @MainActor
    @Test func calendarScrollFocusCoordinatorPreparesReferenceDayBeforeRendering() async throws {
        let referenceDay = DayKey(year: 2026, month: 6, day: 23)
        let earlierDay = DayKey(year: 2026, month: 4, day: 23)
        let coordinator = CalendarScrollFocusCoordinator()

        #expect(!coordinator.canRenderContent(referenceDay: referenceDay, itemIDs: [earlierDay, referenceDay]))

        coordinator.prepareContentPosition(referenceDay: referenceDay, itemIDs: [earlierDay, referenceDay])

        #expect(coordinator.scrollPosition == referenceDay)
        #expect(coordinator.canRenderContent(referenceDay: referenceDay, itemIDs: [earlierDay, referenceDay]))
    }

    @MainActor
    @Test func calendarScrollFocusCoordinatorIgnoresInitialNonReferencePositionThenNotifiesUserScroll() async throws {
        let referenceDay = DayKey(year: 2026, month: 6, day: 23)
        let earlierDay = DayKey(year: 2026, month: 4, day: 23)
        let nextDay = DayKey(year: 2026, month: 6, day: 24)
        let coordinator = CalendarScrollFocusCoordinator()
        var notifiedDays: [DayKey] = []

        coordinator.prepareContentPosition(referenceDay: referenceDay, itemIDs: [earlierDay, referenceDay, nextDay])
        coordinator.notifyScrollReferenceDayIfNeeded(
            earlierDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )
        coordinator.notifyScrollReferenceDayIfNeeded(
            referenceDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )
        coordinator.notifyScrollReferenceDayIfNeeded(
            nextDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )

        #expect(notifiedDays == [nextDay])
    }

    @MainActor
    @Test func calendarScrollFocusCoordinatorKeepsContinuousUserScrollAfterReferenceChange() async throws {
        let referenceDay = DayKey(year: 2026, month: 6, day: 23)
        let nextDay = DayKey(year: 2026, month: 6, day: 24)
        let followingDay = DayKey(year: 2026, month: 6, day: 25)
        let itemIDs = [referenceDay, nextDay, followingDay]
        let coordinator = CalendarScrollFocusCoordinator()
        var notifiedDays: [DayKey] = []

        coordinator.prepareContentPosition(referenceDay: referenceDay, itemIDs: itemIDs)
        coordinator.notifyScrollReferenceDayIfNeeded(
            referenceDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )
        coordinator.notifyScrollReferenceDayIfNeeded(
            nextDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )
        coordinator.alignAfterReferenceDayChanged(to: nextDay, itemIDs: itemIDs)
        coordinator.notifyScrollReferenceDayIfNeeded(
            followingDay,
            currentReferenceDay: nextDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )

        #expect(notifiedDays == [nextDay, followingDay])
    }
}
