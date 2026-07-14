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
                Button("Today", action: onTodayTapped)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.accentColor)
                    .buttonStyle(.plain)
                    .accessibilityLabel("오늘로 이동")
            }

            Button(action: onGoogleCalendarConnectTapped) {
                Image(systemName: "calendar.badge.plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 36, height: 36)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Google Calendar 연동")

            Button(action: onCreateTapped) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 36, height: 36)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("일정 추가")
        }
        .padding(.horizontal, 20)
        .frame(height: 58)
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
