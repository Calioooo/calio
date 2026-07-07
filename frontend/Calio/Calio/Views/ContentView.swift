//
//  ContentView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 1
    @StateObject private var viewModel = CalendarHomeViewModel()
    
    var body: some View {
        TabView(selection: $selectedTab) {
            CalendarHomeView(viewModel: viewModel)
                .tabItem {
                    Image(systemName: "calendar")
                    Text("Home")
                }
                .tag(0)
            
            CalendarWeekTimelineTestView(viewModel: viewModel)
                .tabItem {
                    Image(systemName: "calendar.day.timeline.left")
                    Text("Week")
                }
                .tag(1)
            
            CalendarMonthScheduleTestView(viewModel: viewModel)
                .tabItem {
                    Image(systemName: "calendar")
                    Text("Month")
                }
                .tag(2)
        }
    }
}

private struct CalendarWeekTimelineTestView: View {
    @ObservedObject var viewModel: CalendarHomeViewModel
    @State private var isShowingEventCreationView = false
    
    var body: some View {
        CalendarWeekTimelineView(
            items: viewModel.loadedDateCellItems,
            tags: viewModel.tags,
            referenceDay: viewModel.referenceDay,
            eventAreaState: viewModel.referenceEventAreaState,
            onSelectedDay: viewModel.setReferenceDay(_:),
            onVisibleRangeChanged: viewModel.loadAdditionalEventsIfNeeded(visibleRange:),
            onSelectedYearMonth: viewModel.selectYearMonth(year:month:),
            showsTodayButton: !viewModel.isReferenceDayToday,
            onTodayTapped: viewModel.moveToToday,
            onCreateTapped: startCreatingEvent,
            onRetryEvents: viewModel.retryReferenceMonthEvents,
            isEventMutating: viewModel.mutationState.isMutating,
            isTagMutating: viewModel.tagMutationState.isMutating,
            eventMutationFailureMessage: viewModel.mutationState.failureMessage,
            tagMutationFailureMessage: viewModel.tagMutationState.failureMessage,
            onResetEventMutation: viewModel.resetMutationState,
            onResetTagMutation: viewModel.resetTagMutationState,
            onFetchRecurrenceEvent: viewModel.fetchRecurrenceEvent(recurrenceId:),
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
        .sheet(isPresented: $isShowingEventCreationView) {
            CalendarEventCreationView(
                referenceDay: viewModel.referenceDay,
                tags: viewModel.tags,
                isSaving: viewModel.createState.isSaving,
                isTagMutating: viewModel.tagMutationState.isMutating,
                failureMessage: viewModel.createState.failureMessage,
                tagMutationFailureMessage: viewModel.tagMutationState.failureMessage,
                onSave: { input in
                    await viewModel.createEvent(input)
                },
                onResetTagMutation: viewModel.resetTagMutationState,
                onCreateCustomTag: viewModel.createCustomTag(_:),
                onUpdateCustomTag: viewModel.updateCustomTag(_:input:),
                onDeleteCustomTag: viewModel.deleteCustomTag(_:)
            )
        }
    }

    private func startCreatingEvent() {
        viewModel.resetCreateState()
        isShowingEventCreationView = true
    }
}

private struct CalendarMonthScheduleTestView: View {
    @ObservedObject var viewModel: CalendarHomeViewModel
    @State private var isShowingEventCreationView = false
    @State private var creationDateRange: CalendarDateRange?
    @State private var creationDay: DayKey?
    
    var body: some View {
        CalendarMonthScheduleView(
            items: viewModel.loadedDateCellItems,
            referenceDay: viewModel.referenceDay,
            onSelectedDay: viewModel.setReferenceDay(_:),
            onMonthChanged: viewModel.moveMonthToFirstDay(by:),
            onSelectedYearMonth: viewModel.selectMonthFirstDay(year:month:),
            showsTodayButton: !viewModel.isReferenceDayToday,
            onTodayTapped: viewModel.moveToToday,
            onCreateTapped: startCreatingEvent,
            onCreateInRangeTapped: startCreatingEvent(in:),
            onCreateInDayTapped: startCreatingEvent(on:)
        )
        .task {
            viewModel.loadTagsIfNeeded()
            viewModel.loadInitialIfNeeded()
        }
        .sheet(isPresented: $isShowingEventCreationView) {
            CalendarEventCreationView(
                referenceDay: creationDateRange?.startDay ?? creationDay ?? viewModel.referenceDay,
                initialDateRange: creationDateRange,
                tags: viewModel.tags,
                isSaving: viewModel.createState.isSaving,
                isTagMutating: viewModel.tagMutationState.isMutating,
                failureMessage: viewModel.createState.failureMessage,
                tagMutationFailureMessage: viewModel.tagMutationState.failureMessage,
                onSave: { input in
                    await viewModel.createEvent(input)
                },
                onResetTagMutation: viewModel.resetTagMutationState,
                onCreateCustomTag: viewModel.createCustomTag(_:),
                onUpdateCustomTag: viewModel.updateCustomTag(_:input:),
                onDeleteCustomTag: viewModel.deleteCustomTag(_:)
            )
        }
    }

    private func startCreatingEvent() {
        creationDateRange = nil
        creationDay = nil
        viewModel.resetCreateState()
        isShowingEventCreationView = true
    }

    private func startCreatingEvent(in dateRange: CalendarDateRange) {
        creationDateRange = dateRange
        creationDay = nil
        viewModel.resetCreateState()
        isShowingEventCreationView = true
    }

    private func startCreatingEvent(on day: DayKey) {
        creationDateRange = nil
        creationDay = day
        viewModel.resetCreateState()
        isShowingEventCreationView = true
    }
}

#Preview {
    ContentView()
}
