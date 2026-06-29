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
            
            CalendarDayTimelineTestView(viewModel: viewModel)
                .tabItem {
                    Image(systemName: "calendar.day.timeline.leading")
                    Text("Day")
                }
                .tag(2)
        }
    }
}

private struct CalendarWeekTimelineTestView: View {
    @ObservedObject var viewModel: CalendarHomeViewModel
    
    var body: some View {
        CalendarWeekTimelineView(
            items: viewModel.loadedDateCellItems,
            referenceDay: viewModel.referenceDay,
            eventAreaState: viewModel.referenceEventAreaState,
            onSelectedDay: viewModel.setReferenceDay(_:),
            onVisibleRangeChanged: viewModel.loadAdditionalEventsIfNeeded(visibleRange:),
            onSelectedYearMonth: viewModel.selectYearMonth(year:month:),
            onRetryEvents: viewModel.retryReferenceMonthEvents,
            isEventMutating: viewModel.mutationState.isMutating,
            eventMutationFailureMessage: viewModel.mutationState.failureMessage,
            onResetEventMutation: viewModel.resetMutationState,
            onFetchRecurrenceEvent: viewModel.fetchRecurrenceEvent(recurrenceId:),
            onUpdateSingleEvent: viewModel.updateSingleEvent(_:input:),
            onUpdateRecurrenceOccurrence: viewModel.updateRecurrenceOccurrence(_:input:),
            onUpdateRecurrenceSeries: viewModel.updateRecurrenceSeries(recurrenceId:input:),
            onDeleteSingleEvent: viewModel.deleteSingleEvent(_:),
            onDeleteRecurrenceOccurrence: viewModel.deleteRecurrenceOccurrence(_:),
            onDeleteRecurrenceSeries: viewModel.deleteRecurrenceSeries(_:)
        )
        .task {
            viewModel.loadInitialIfNeeded()
        }
    }
}

private struct CalendarDayTimelineTestView: View {
    @ObservedObject var viewModel: CalendarHomeViewModel
    
    var body: some View {
        CalendarDayTimelineView(
            items: viewModel.loadedDateCellItems,
            referenceDay: viewModel.referenceDay,
            eventAreaState: viewModel.referenceEventAreaState,
            onRetryEvents: viewModel.retryReferenceMonthEvents
        )
        .task {
            viewModel.loadInitialIfNeeded()
        }
    }
}

#Preview {
    ContentView()
}
