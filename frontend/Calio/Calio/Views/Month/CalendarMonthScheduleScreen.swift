//
//  CalendarMonthScheduleScreen.swift
//  Calio
//
//  Created by Codex on 7/7/26.
//

import SwiftUI

struct CalendarMonthScheduleScreen: View {
    @ObservedObject var viewModel: CalendarHomeViewModel
    @State private var isShowingEventCreationView = false
    @State private var selectedDateRange: CalendarDateRange?
    @State private var selectedDay: DayKey?
    private let onGoogleCalendarConnectTapped: () -> Void

    init(
        viewModel: CalendarHomeViewModel,
        onGoogleCalendarConnectTapped: @escaping () -> Void = {}
    ) {
        self.viewModel = viewModel
        self.onGoogleCalendarConnectTapped = onGoogleCalendarConnectTapped
    }

    var body: some View {
        CalendarMonthScheduleView(
            items: viewModel.loadedDateCellItems,
            referenceDay: viewModel.referenceDay,
            onSelectedDay: viewModel.setReferenceDay(_:),
            onMonthChanged: viewModel.moveMonthToFirstDay(by:),
            onSelectedYearMonth: viewModel.selectMonthFirstDay(year:month:),
            showsTodayButton: !viewModel.isReferenceDayToday,
            onTodayTapped: viewModel.moveToToday,
            onGoogleCalendarConnectTapped: onGoogleCalendarConnectTapped,
            onCreateTapped: {
                startCreatingEvent()
            },
            onCreateInRangeTapped: { dateRange in
                startCreatingEvent(in: dateRange)
            },
            onCreateInDayTapped: { day in
                startCreatingEvent(on: day)
            }
        )
        .task {
            viewModel.loadTagsIfNeeded()
            viewModel.loadInitialIfNeeded()
        }
        .eventCreationSheet(
            isPresented: $isShowingEventCreationView,
            viewModel: viewModel,
            referenceDay: selectedDateRange?.startDay ?? selectedDay ?? viewModel.referenceDay,
            initialDateRange: selectedDateRange
        )
    }

    private func startCreatingEvent(
        in dateRange: CalendarDateRange? = nil,
        on day: DayKey? = nil
    ) {
        selectedDateRange = dateRange
        selectedDay = day
        viewModel.resetCreateState()
        isShowingEventCreationView = true
    }
}
