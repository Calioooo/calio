//
//  ContentView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 1
    
    var body: some View {
        TabView(selection: $selectedTab) {
            CalendarHomeView()
                .tabItem {
                    Image(systemName: "calendar")
                    Text("Home")
                }
                .tag(0)
            
            CalendarWeekTimelineTestView()
                .tabItem {
                    Image(systemName: "calendar.day.timeline.left")
                    Text("Week")
                }
                .tag(1)
        }
    }
}

private struct CalendarWeekTimelineTestView: View {
    @StateObject private var viewModel = CalendarHomeViewModel()
    
    var body: some View {
        CalendarWeekTimelineView(
            items: viewModel.loadedDateCellItems,
            focusedDay: viewModel.state.focusedDay,
            onSelectedDay: viewModel.focusDay(_:)
        )
        .task {
            viewModel.loadInitialIfNeeded()
        }
    }
}

#Preview {
    ContentView()
}
