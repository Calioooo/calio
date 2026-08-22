//
//  CalendarTopBarView.swift
//  Calio
//
//  Created by Codex on 7/2/26.
//

import SwiftUI

struct CalendarTopBarView: View {
    let referenceDay: DayKey
    let showsTodayButton: Bool
    let onSelectedYearMonth: (Int, Int) -> Void
    let onTodayTapped: () -> Void
    let onGoogleCalendarConnectTapped: () -> Void
    let onCreateTapped: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            CalendarYearMonthTitleView(
                referenceDay: referenceDay,
                onSelectedYearMonth: onSelectedYearMonth
            )
            .frame(maxWidth: .infinity, alignment: .leading)

            if showsTodayButton {
                Button("오늘", action: onTodayTapped)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.calioPrimary)
                    .padding(.horizontal, 10)
                    .frame(minHeight: 36)
                    .background(Capsule().fill(Color.calioSelection))
                    .accessibilityLabel("오늘로 이동")
                    .accessibilityIdentifier("calendar_navigation_today")
            }

            Button(action: onGoogleCalendarConnectTapped) {
                Image(systemName: "calendar.badge.plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.calioTextSecondary)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Color.calioSurface))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Google Calendar 연동")
            .accessibilityHint("Google Calendar 인증을 시작합니다")
            .accessibilityIdentifier("calendar_navigation_google_connect")

            Button(action: onCreateTapped) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Color.calioBrand))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("일정 추가")
            .accessibilityIdentifier("calendar_navigation_add_event")
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 60)
        .background(Color.calioBackground)
        .accessibilityIdentifier("calendar_navigation_top_bar")
    }
}

#Preview {
    CalendarTopBarView(
        referenceDay: DayKey(date: Date()),
        showsTodayButton: true,
        onSelectedYearMonth: { _, _ in },
        onTodayTapped: {},
        onGoogleCalendarConnectTapped: {},
        onCreateTapped: {}
    )
}
