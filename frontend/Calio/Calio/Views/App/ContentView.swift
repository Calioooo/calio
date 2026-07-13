//
//  ContentView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 0
    @StateObject private var viewModel = CalendarHomeViewModel()
    
    var body: some View {
        TabView(selection: $selectedTab) {
            CalendarHomeView(viewModel: viewModel)
                .tabItem {
                    Image(systemName: "calendar")
                    Text("Home")
                }
                .tag(0)
            
            CalendarWeekTimelineScreen(viewModel: viewModel)
                .tabItem {
                    Image(systemName: "calendar.day.timeline.left")
                    Text("Week")
                }
                .tag(1)
            
            CalendarMonthScheduleScreen(viewModel: viewModel)
                .tabItem {
                    Image(systemName: "calendar")
                    Text("Month")
                }
                .tag(2)
        }
    }
}

#Preview {
    ContentView()
}
