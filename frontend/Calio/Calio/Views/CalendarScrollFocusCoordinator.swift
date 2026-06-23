//
//  CalendarScrollFocusCoordinator.swift
//  Calio
//
//  Created by Codex on 6/23/26.
//

import Combine
import Foundation

@MainActor
final class CalendarScrollFocusCoordinator: ObservableObject {
    @Published var scrollPosition: DayKey?

    private let programmaticAlignmentDelay: UInt64 = 300_000_000
    private var hasPreparedContentPosition = false
    private var programmaticTarget: DayKey?
    private var lastUserSelectedFocusedDay: DayKey?
    private var lastSyncedFocusedDay: DayKey?
    private var resetProgrammaticAlignmentTask: Task<Void, Never>?

    func canRenderContent(
        focusedDay: DayKey,
        itemIDs: [DayKey]
    ) -> Bool {
        hasPreparedContentPosition && itemIDs.contains(focusedDay)
    }

    func prepareContentPosition(
        focusedDay: DayKey,
        itemIDs: [DayKey]
    ) {
        guard itemIDs.contains(focusedDay) else { return }
        guard !hasPreparedContentPosition || scrollPosition.map({ !itemIDs.contains($0) }) == true else { return }

        hasPreparedContentPosition = true
        alignProgrammatically(to: focusedDay, itemIDs: itemIDs)
    }

    func alignAfterFocusedDayChanged(
        to day: DayKey,
        itemIDs: [DayKey]
    ) {
        if lastUserSelectedFocusedDay == day {
            lastUserSelectedFocusedDay = nil
            return
        }

        alignProgrammatically(to: day, itemIDs: itemIDs)
    }

    func alignAfterItemsChanged(
        focusedDay: DayKey,
        itemIDs: [DayKey]
    ) {
        alignProgrammatically(to: focusedDay, itemIDs: itemIDs)
    }

    func notifyUserSelectedFocusedDay(
        _ day: DayKey,
        onFocusedDayChanged: (DayKey) -> Void
    ) {
        lastUserSelectedFocusedDay = day
        notifyFocusedDay(day, onFocusedDayChanged: onFocusedDayChanged)
    }

    func notifyScrollFocusedDayIfNeeded(
        _ day: DayKey?,
        currentFocusedDay: DayKey,
        onFocusedDayChanged: (DayKey) -> Void
    ) {
        if let programmaticTarget {
            clearProgrammaticAlignmentIfReached(programmaticTarget, by: day)
            return
        }

        guard let day else { return }
        guard lastSyncedFocusedDay != nil || day == currentFocusedDay else { return }
        guard day != lastSyncedFocusedDay else { return }

        notifyFocusedDay(day, onFocusedDayChanged: onFocusedDayChanged)
    }

    func cancel() {
        resetProgrammaticAlignmentTask?.cancel()
        resetProgrammaticAlignmentTask = nil
        programmaticTarget = nil
    }

    private func alignProgrammatically(
        to day: DayKey,
        itemIDs: [DayKey]
    ) {
        guard itemIDs.contains(day) else { return }

        programmaticTarget = day
        scrollPosition = day
        scheduleProgrammaticAlignmentReset(for: day)
    }

    private func notifyFocusedDay(
        _ day: DayKey,
        onFocusedDayChanged: (DayKey) -> Void
    ) {
        lastSyncedFocusedDay = day
        onFocusedDayChanged(day)
    }

    private func clearProgrammaticAlignmentIfReached(
        _ target: DayKey,
        by day: DayKey?
    ) {
        guard day == target else { return }

        lastSyncedFocusedDay = target
        programmaticTarget = nil
        resetProgrammaticAlignmentTask?.cancel()
        resetProgrammaticAlignmentTask = nil
    }

    private func scheduleProgrammaticAlignmentReset(for target: DayKey) {
        resetProgrammaticAlignmentTask?.cancel()
        resetProgrammaticAlignmentTask = Task {
            try? await Task.sleep(nanoseconds: programmaticAlignmentDelay)
            guard !Task.isCancelled else { return }

            await MainActor.run {
                guard self.programmaticTarget == target else { return }
                self.programmaticTarget = nil
                self.resetProgrammaticAlignmentTask = nil
            }
        }
    }
}
