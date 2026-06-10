//
//  CalendarHomeView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct CalendarHomeView: View {
    @StateObject private var viewModel = CalendarHomeViewModel()
    @State private var displayMode: CalendarDisplayMode = .week
    @State private var activeScrollSource: CalendarScrollSource = .idle
    @State private var scrollProgress: CGFloat = 0
    @State private var stripCellWidth: CGFloat = 1
    @State private var stripTargetOffset: CalendarScrollTarget?
    @State private var eventTargetOffset: CalendarScrollTarget?
    
    private let minimumStripViewHeight: CGFloat = 110
    private let stripViewHeightRatio: CGFloat = 0.2
    private let minimumMonthViewHeight: CGFloat = 260
    private let monthViewHeightRatio: CGFloat = 0.42
    

    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                calendarHeader(in: geometry)

                CalendarScheduleDrawerView(
                    items: viewModel.loadedDateCellItems,
                    focusedDay: viewModel.state.focusedDay,
                    displayMode: displayMode,
                    onFocusedDayChanged: viewModel.focusDay(_:),
                    onDragEnded: updateDisplayMode(after:),
                    eventTargetOffset: eventTargetOffset,
                    onEventScrollProgressChanged: handleEventScroll(progress:visibleRange:),
                    onEventScrollEnded: { progress in
                        snapAndCommitFocus(from: .event, progress: progress)
                    }
                )
            }
            .animation(.easeInOut(duration: 0.2), value: displayMode)
            .task {
                viewModel.loadInitialIfNeeded()
            }
            .onChange(of: viewModel.loadedDateCellItems.map(\.id)) { _, _ in
                alignTargetsToFocusedDay()
            }
            .onChange(of: viewModel.state.focusedDay) { _, _ in
                alignTargetsToFocusedDay()
            }
            .onChange(of: viewModel.prependScrollCompensation?.id) { _, _ in
                compensatePastPrependIfNeeded()
            }
        }
    }

    @ViewBuilder
    private func calendarHeader(in geometry: GeometryProxy) -> some View {
        switch displayMode {
        case .week:
            CalendarDateStripView(
                items: viewModel.loadedDateCellItems,
                focusedDay: viewModel.state.focusedDay,
                onFocusedDayChanged: viewModel.focusDay(_:),
                targetOffset: stripTargetOffset,
                onScrollProgressChanged: handleStripScroll(progress:visibleRange:),
                onScrollEnded: { progress in
                    snapAndCommitFocus(from: .strip, progress: progress)
                },
                onCellWidthChanged: { cellWidth in
                    stripCellWidth = cellWidth
                    alignTargetsToFocusedDay()
                }
            )
            .frame(height: weekHeaderHeight(in: geometry))
            .transition(.opacity)
            .accessibilityIdentifier("calendar_header_week")

        case .month:
            CalendarMonthView(items: viewModel.visibleDateCellItems)
                .frame(height: monthHeaderHeight(in: geometry))
                .transition(.opacity)
        }
    }

    private func updateDisplayMode(after translation: CGSize) {
        let nextDisplayMode = displayMode.resolved(
            afterDragTranslationHeight: translation.height
        )

        guard nextDisplayMode != displayMode else {
            return
        }

        displayMode = nextDisplayMode
    }

    private func handleStripScroll(
        progress: CGFloat,
        visibleRange: CalendarVisibleIndexRange
    ) {
        activeScrollSource = .strip
        scrollProgress = progress
        viewModel.loadAdditionalEventsIfNeeded(visibleRange: visibleRange)
        eventTargetOffset = CalendarScrollTarget(
            offset: CalendarScrollMetrics.targetOffset(
                progress: progress,
                itemExtent: CalendarScrollMetrics.eventRowHeight
            )
        )
    }

    private func handleEventScroll(
        progress: CGFloat,
        visibleRange: CalendarVisibleIndexRange
    ) {
        activeScrollSource = .event
        scrollProgress = progress
        viewModel.loadAdditionalEventsIfNeeded(visibleRange: visibleRange)
        stripTargetOffset = CalendarScrollTarget(
            offset: CalendarScrollMetrics.targetOffset(
                progress: progress,
                itemExtent: stripCellWidth
            )
        )
    }

    private func snapAndCommitFocus(
        from source: CalendarScrollSource,
        progress: CGFloat
    ) {
        guard activeScrollSource == source || activeScrollSource == .idle else {
            return
        }

        guard let finalIndex = CalendarScrollMetrics.nearestIndex(
            progress: progress,
            itemCount: viewModel.loadedDateCount
        ) else {
            return
        }

        scrollProgress = CGFloat(finalIndex)
        activeScrollSource = .idle
        setTargets(to: scrollProgress)

        guard let focusedDay = viewModel.day(at: finalIndex) else {
            return
        }

        viewModel.focusDay(focusedDay)
    }

    private func alignTargetsToFocusedDay() {
        guard activeScrollSource == .idle else {
            return
        }

        guard let focusedIndex = viewModel.index(of: viewModel.state.focusedDay) else {
            return
        }

        scrollProgress = CGFloat(focusedIndex)
        setTargets(to: scrollProgress)
    }

    private func compensatePastPrependIfNeeded() {
        guard let compensation = viewModel.prependScrollCompensation else {
            return
        }

        guard activeScrollSource != .idle else {
            return
        }

        scrollProgress += CGFloat(compensation.insertedCount)
        setTargets(to: scrollProgress)
    }

    private func setTargets(to progress: CGFloat) {
        stripTargetOffset = CalendarScrollTarget(
            offset: CalendarScrollMetrics.targetOffset(
                progress: progress,
                itemExtent: stripCellWidth
            )
        )
        eventTargetOffset = CalendarScrollTarget(
            offset: CalendarScrollMetrics.targetOffset(
                progress: progress,
                itemExtent: CalendarScrollMetrics.eventRowHeight
            )
        )
    }

    private func weekHeaderHeight(in geometry: GeometryProxy) -> CGFloat {
        max(minimumStripViewHeight, geometry.size.height * stripViewHeightRatio)
    }

    private func monthHeaderHeight(in geometry: GeometryProxy) -> CGFloat {
        max(minimumMonthViewHeight, geometry.size.height * monthViewHeightRatio)
    }
}

#Preview("iPhone SE") {
    CalendarHomeView()
}

#Preview("iPhone 15 Pro") {
    CalendarHomeView()
}

#Preview("Dark Mode") {
    CalendarHomeView()
        .preferredColorScheme(.dark)
}
