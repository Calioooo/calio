//
//  CalendarHomeView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct CalendarHomeView: View {
    @StateObject private var viewModel: CalendarHomeViewModel
    @State private var displayMode: CalendarDisplayMode = .week
    @State private var isShowingEventCreationView = false
    private let onGoogleCalendarConnectTapped: () -> Void
    
    private let minimumStripViewHeight: CGFloat = 110
    private let stripViewHeightRatio: CGFloat = 0.2
    private let minimumMonthViewHeight: CGFloat = 380
    private let monthViewHeightRatio: CGFloat = 0.42
    
    init(
        viewModel: CalendarHomeViewModel,
        onGoogleCalendarConnectTapped: @escaping () -> Void = {}
    ) {
        _viewModel = StateObject(wrappedValue: viewModel)
        self.onGoogleCalendarConnectTapped = onGoogleCalendarConnectTapped
    }
    

    var body: some View {
        GeometryReader { geometry in
            let loadedDateCellItems = viewModel.loadedDateCellItems

            VStack(spacing: 0) {
                calendarTopBar
                calendarHeader(in: geometry, items: loadedDateCellItems)

                CalendarScheduleDrawerView(
                    items: loadedDateCellItems,
                    tags: viewModel.tags,
                    referenceDay: viewModel.referenceDay,
                    displayMode: displayMode,
                    eventLoadState: viewModel.eventLoadState,
                    onReferenceDayChanged: viewModel.setReferenceDay(_:),
                    onVisibleRangeChanged: viewModel.loadAdditionalEventsIfNeeded(visibleRange:),
                    onRetryEventLoading: viewModel.retryEventLoading,
                    onDragEnded: updateDisplayMode(after:),
                    isEventMutating: viewModel.mutationState.isMutating,
                    isTagMutating: viewModel.tagMutationState.isMutating,
                    eventMutationFailureMessage: viewModel.mutationState.failureMessage,
                    tagMutationFailureMessage: viewModel.tagMutationState.failureMessage,
                    onResetEventMutation: viewModel.resetMutationState,
                    onResetTagMutation: viewModel.resetTagMutationState,
                    onFetchRecurrenceEvent: viewModel.fetchRecurrenceEvent(recurrenceId:),
                    onUpdateImportantEvent: viewModel.updateImportantEvent(_:importantEvent:),
                    onUpdateSingleEvent: viewModel.updateSingleEvent(_:input:),
                    onUpdateRecurrenceOccurrence: viewModel.updateRecurrenceOccurrence(_:input:),
                    onUpdateRecurrenceSeries: viewModel.updateRecurrenceSeries(recurrenceId:input:),
                    onDeleteSingleEvent: viewModel.deleteSingleEvent(_:),
                    onDeleteRecurrenceOccurrence: viewModel.deleteRecurrenceOccurrence(_:),
                    onDeleteRecurrenceSeries: viewModel.deleteRecurrenceSeries(_:),
                    onCreateCustomTag: viewModel.createCustomTag(_:),
                    onUpdateCustomTag: viewModel.updateCustomTag(_:input:),
                    onDeleteCustomTag: viewModel.deleteCustomTag(_:)
                )
            }
            .background(Color.calioBackground)
            .animation(.easeInOut(duration: 0.2), value: displayMode)
            .task {
                viewModel.loadTagsIfNeeded()
                viewModel.loadInitialIfNeeded()
            }
            .eventCreationSheet(
                isPresented: $isShowingEventCreationView,
                viewModel: viewModel,
                referenceDay: viewModel.referenceDay
            )
        }
    }
    
    private var calendarTopBar: some View {
        CalendarTopBarView(
            referenceDay: viewModel.referenceDay,
            showsTodayButton: !viewModel.isReferenceDayToday,
            onSelectedYearMonth: viewModel.selectYearMonth(year:month:),
            onTodayTapped: viewModel.moveToToday,
            onGoogleCalendarConnectTapped: onGoogleCalendarConnectTapped,
            onCreateTapped: startCreatingEvent
        )
    }

    @ViewBuilder
    private func calendarHeader(
        in geometry: GeometryProxy,
        items: [CalendarDayItem]
    ) -> some View {
        switch displayMode {
        case .week:
            CalendarDateStripView(
                items: items,
                referenceDay: viewModel.referenceDay,
                onReferenceDayChanged: viewModel.setReferenceDay(_:)
            )
            .frame(height: weekHeaderHeight(in: geometry))
            .transition(.opacity)
            .accessibilityIdentifier("calendar_header_week")

        case .month:
            CalendarMonthView(
                items: items,
                referenceDay: viewModel.referenceDay,
                onSelectedDay: viewModel.setReferenceDay(_:),
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
    
    private func startCreatingEvent() {
        viewModel.resetCreateState()
        isShowingEventCreationView = true
    }
}

#Preview("iPhone SE") {
    CalendarHomeView(viewModel: CalendarHomeViewModel())
}

#Preview("iPhone 15 Pro") {
    CalendarHomeView(viewModel: CalendarHomeViewModel())
}

#Preview("Dark Mode") {
    CalendarHomeView(viewModel: CalendarHomeViewModel())
        .preferredColorScheme(.dark)
}
