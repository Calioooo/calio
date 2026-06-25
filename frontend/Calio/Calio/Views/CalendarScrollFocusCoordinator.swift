//
//  CalendarScrollFocusCoordinator.swift
//  Calio
//
//  Created by Codex on 6/23/26.
//

import Foundation

@MainActor
final class CalendarScrollFocusCoordinator: ObservableObject {
    @Published var scrollPosition: DayKey?

    private let programmaticAlignmentDelay: UInt64 = 300_000_000
    private var hasPreparedContentPosition = false
    private var programmaticTarget: DayKey?
    private var lastLocallyRequestedReferenceDay: DayKey?
    private var lastSyncedReferenceDay: DayKey?
    private var resetProgrammaticAlignmentTask: Task<Void, Never>?

    func canRenderContent(
        referenceDay: DayKey,
        itemIDs: [DayKey]
    ) -> Bool {
        hasPreparedContentPosition && itemIDs.contains(referenceDay)
    }

    func prepareContentPosition(
        referenceDay: DayKey,
        itemIDs: [DayKey]
    ) {
        guard itemIDs.contains(referenceDay) else { return }
        guard !hasPreparedContentPosition || scrollPosition.map({ !itemIDs.contains($0) }) == true else { return }

        hasPreparedContentPosition = true
        alignProgrammatically(to: referenceDay, itemIDs: itemIDs)
    }

    func alignAfterReferenceDayChanged(
        to day: DayKey,
        itemIDs: [DayKey]
    ) {
        if lastLocallyRequestedReferenceDay == day {
            lastLocallyRequestedReferenceDay = nil
            return
        }

        alignProgrammatically(to: day, itemIDs: itemIDs)
    }

    func alignAfterItemsChanged(
        referenceDay: DayKey,
        itemIDs: [DayKey]
    ) {
        alignProgrammatically(to: referenceDay, itemIDs: itemIDs)
    }

    func notifyUserSelectedReferenceDay(
        _ day: DayKey,
        onReferenceDayChanged: (DayKey) -> Void
    ) {
        lastLocallyRequestedReferenceDay = day
        notifyReferenceDay(day, onReferenceDayChanged: onReferenceDayChanged)
    }

    func notifyScrollReferenceDayIfNeeded(
        _ day: DayKey?,
        currentReferenceDay: DayKey,
        onReferenceDayChanged: (DayKey) -> Void
    ) {
        if let programmaticTarget {
            clearProgrammaticAlignmentIfReached(programmaticTarget, by: day)
            return
        }

        guard let day else { return }
        guard lastSyncedReferenceDay != nil || day == currentReferenceDay else { return }
        guard day != lastSyncedReferenceDay else { return }

        lastLocallyRequestedReferenceDay = day
        notifyReferenceDay(day, onReferenceDayChanged: onReferenceDayChanged)
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
        lastSyncedReferenceDay = day
        scrollPosition = day
        scheduleProgrammaticAlignmentReset(for: day)
    }

    private func notifyReferenceDay(
        _ day: DayKey,
        onReferenceDayChanged: (DayKey) -> Void
    ) {
        lastSyncedReferenceDay = day
        onReferenceDayChanged(day)
    }

    private func clearProgrammaticAlignmentIfReached(
        _ target: DayKey,
        by day: DayKey?
    ) {
        guard day == target else { return }

        lastSyncedReferenceDay = target
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
