//
//  CalendarTopBarView.swift
//  Calio
//
//  Created by Codex on 7/2/26.
//

import SwiftUI

struct CalendarTopBarView: View {
    @Environment(\.sizeCategory) private var sizeCategory
    let referenceDay: DayKey
    let showsTodayButton: Bool
    let onSelectedYearMonth: (Int, Int) -> Void
    let onTodayTapped: () -> Void
    let onGoogleCalendarConnectTapped: () -> Void
    let onCreateTapped: () -> Void

    var body: some View {
        Group {
            if sizeCategory.isAccessibilityCategory {
                accessibilityHeader
            } else {
                standardHeader
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 10)
        .background(Color.calioBackground)
        .accessibilityIdentifier("calendar_navigation_top_bar")
    }

    private var standardHeader: some View {
        HStack(spacing: 10) {
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
                    .frame(minWidth: 44, minHeight: 44)
                    .background(Capsule().fill(Color.calioSelection))
                    .accessibilityLabel("오늘로 이동")
                    .accessibilityIdentifier("calendar_navigation_today")
            }

            Button(action: onGoogleCalendarConnectTapped) {
                Image(systemName: "calendar.badge.plus")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.calioTextSecondary)
                    .frame(width: 40, height: 40)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.calioSurface))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Google Calendar 연동")
            .accessibilityHint("Google Calendar 인증을 시작합니다")
            .accessibilityIdentifier("calendar_navigation_google_connect")

            Button(action: onCreateTapped) {
                Label("일정 추가", systemImage: "plus")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 13)
                    .frame(minHeight: 40)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.calioBrand))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("일정 추가")
            .accessibilityIdentifier("calendar_navigation_add_event")
        }
        .frame(minHeight: 64)
    }

    private var accessibilityHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                CalendarYearMonthTitleView(
                    referenceDay: referenceDay,
                    onSelectedYearMonth: onSelectedYearMonth
                )
                .frame(maxWidth: .infinity, alignment: .leading)

                if showsTodayButton {
                    todayButton
                }
            }

            HStack(spacing: 12) {
                googleCalendarButton
                createButton
            }
        }
    }

    private var todayButton: some View {
        Button("오늘", action: onTodayTapped)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.calioPrimary)
            .padding(.horizontal, 12)
            .frame(minHeight: 40)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.calioSelection))
            .accessibilityLabel("오늘로 이동")
            .accessibilityIdentifier("calendar_navigation_today")
    }

    private var googleCalendarButton: some View {
        Button(action: onGoogleCalendarConnectTapped) {
            Image(systemName: "calendar.badge.plus")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.calioTextSecondary)
                .frame(width: 40, height: 40)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.calioSurface))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Google Calendar 연동")
        .accessibilityHint("Google Calendar 인증을 시작합니다")
        .accessibilityIdentifier("calendar_navigation_google_connect")
    }

    private var createButton: some View {
        Button(action: onCreateTapped) {
            Label("일정 추가", systemImage: "plus")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 13)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(RoundedRectangle(cornerRadius: 10).fill(Color.calioBrand))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("일정 추가")
        .accessibilityIdentifier("calendar_navigation_add_event")
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
