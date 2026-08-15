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
            }

            Button(action: onGoogleCalendarConnectTapped) {
                Image(systemName: "calendar.badge.plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.calioTextSecondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Google Calendar 연동")

            Button(action: onCreateTapped) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(Color.calioBrand))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("일정 추가")
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 60)
        .background(Color.calioBackground)
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
