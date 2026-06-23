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
            focusedDay: viewModel.state.focusedDay,
            eventAreaState: viewModel.focusedEventAreaState,
            onSelectedDay: viewModel.focusDay(_:),
            onVisibleRangeChanged: viewModel.loadAdditionalEventsIfNeeded(visibleRange:),
            onSelectedYearMonth: viewModel.selectYearMonth(year:month:),
            onRetryEvents: viewModel.retryFocusedMonthEvents
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
            focusedDay: viewModel.state.focusedDay,
            eventAreaState: viewModel.focusedEventAreaState,
            onRetryEvents: viewModel.retryFocusedMonthEvents
        )
        .task {
            viewModel.loadInitialIfNeeded()
        }
    }
}

#Preview {
    ContentView()
}
