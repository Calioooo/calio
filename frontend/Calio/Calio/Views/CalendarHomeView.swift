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
    
    private let minimumStripViewHeight: CGFloat = 110
    private let stripViewHeightRatio: CGFloat = 0.2
    private let minimumMonthViewHeight: CGFloat = 380
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
                    onVisibleRangeChanged: viewModel.loadAdditionalEventsIfNeeded(visibleRange:),
                    onDragEnded: updateDisplayMode(after:)
                )
            }
            .animation(.easeInOut(duration: 0.2), value: displayMode)
            .task {
                viewModel.loadInitialIfNeeded()
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
                onFocusedDayChanged: viewModel.focusDay(_:)
            )
            .frame(height: weekHeaderHeight(in: geometry))
            .transition(.opacity)
            .accessibilityIdentifier("calendar_header_week")

        case .month:
            CalendarMonthView(
                items: viewModel.loadedDateCellItems,
                focusedDay: viewModel.state.focusedDay,
                onSelectedDay: viewModel.focusDay(_:),
                onMonthChanged: viewModel.moveMonth(by:)
            )
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
