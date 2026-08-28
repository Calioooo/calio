//
//  CalendarWeekTimelineScreen.swift
//  Calio
//
//  Created by Codex on 7/7/26.
//

import SwiftUI

struct CalendarWeekTimelineScreen: View {
    @ObservedObject var viewModel: CalendarHomeViewModel
    @State private var isShowingEventCreationView = false
    private let onGoogleCalendarConnectTapped: () -> Void

    init(
        viewModel: CalendarHomeViewModel,
        onGoogleCalendarConnectTapped: @escaping () -> Void = {}
    ) {
        self.viewModel = viewModel
        self.onGoogleCalendarConnectTapped = onGoogleCalendarConnectTapped
    }

    var body: some View {
        CalendarWeekTimelineView(
            items: viewModel.loadedDateCellItems,
            tags: viewModel.tags,
            referenceDay: viewModel.referenceDay,
            eventLoadState: viewModel.eventLoadState,
            onSelectedDay: viewModel.setReferenceDay(_:),
            onVisibleRangeChanged: viewModel.loadAdditionalEventsIfNeeded(visibleRange:),
            onSelectedYearMonth: viewModel.selectYearMonth(year:month:),
            showsTodayButton: !viewModel.isReferenceDayToday,
            onTodayTapped: viewModel.moveToToday,
            onGoogleCalendarConnectTapped: onGoogleCalendarConnectTapped,
            onCreateTapped: startCreatingEvent,
            onRetryEventLoading: viewModel.retryEventLoading,
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

    private func startCreatingEvent() {
        viewModel.resetCreateState()
        isShowingEventCreationView = true
    }
}
