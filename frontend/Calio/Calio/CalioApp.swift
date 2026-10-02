//
//  CalioApp.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

@main
struct CalioApp: App {
  var body: some Scene {
    WindowGroup {
      rootView
    }
  }

  @ViewBuilder
  private var rootView: some View {
    #if DEBUG
      if ProcessInfo.processInfo.arguments.contains("--ui-testing-calendar-top-bar") {
        CalendarTopBarUITestHost()
      } else {
        ContentView()
      }
    #else
      ContentView()
    #endif
  }
}

#if DEBUG
  private struct CalendarTopBarUITestHost: View {
    var body: some View {
      VStack {
        CalendarTopBarView(
          referenceDay: DayKey(date: Date()),
          showsTodayButton: true,
          onSelectedYearMonth: { _, _ in },
          onTodayTapped: {},
          onGoogleCalendarConnectTapped: {},
          onCreateTapped: {},
          onCreateVoteTapped: {},
          onMyVotesTapped: {}
        )
        Spacer()
      }
      .environment(\.sizeCategory, .accessibilityExtraExtraExtraLarge)
    }
  }
#endif
