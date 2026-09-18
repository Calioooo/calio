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
  let onCreateVoteTapped: (() -> Void)?
  let onMyVotesTapped: (() -> Void)?
  let onCreateTapped: () -> Void

  init(
    referenceDay: DayKey,
    showsTodayButton: Bool,
    onSelectedYearMonth: @escaping (Int, Int) -> Void,
    onTodayTapped: @escaping () -> Void,
    onGoogleCalendarConnectTapped: @escaping () -> Void,
    onCreateTapped: @escaping () -> Void,
    onCreateVoteTapped: (() -> Void)? = nil,
    onMyVotesTapped: (() -> Void)? = nil
  ) {
    self.referenceDay = referenceDay
    self.showsTodayButton = showsTodayButton
    self.onSelectedYearMonth = onSelectedYearMonth
    self.onTodayTapped = onTodayTapped
    self.onGoogleCalendarConnectTapped = onGoogleCalendarConnectTapped
    self.onCreateTapped = onCreateTapped
    self.onCreateVoteTapped = onCreateVoteTapped
    self.onMyVotesTapped = onMyVotesTapped
  }

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
    Group {
      if hasVoteActions {
        voteEnabledHeader
      } else {
        standardCalendarHeader
      }
    }
  }

  private var voteEnabledHeader: some View {
    VStack(spacing: 8) {
      HStack(spacing: 10) {
        CalendarYearMonthTitleView(
          referenceDay: referenceDay,
          onSelectedYearMonth: onSelectedYearMonth
        )
        .frame(maxWidth: .infinity, alignment: .leading)

        if showsTodayButton {
          todayButton
        }

        googleCalendarButton
      }

      HStack(spacing: 8) {
        Spacer(minLength: 0)
        voteActions
        compactCreateButton
      }
    }
  }

  private var standardCalendarHeader: some View {
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

      standardCreateButton
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
        if hasVoteActions {
          voteActions
          compactCreateButton
        } else {
          standardCreateButton
        }
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

  private var standardCreateButton: some View {
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

  private var compactCreateButton: some View {
    Button(action: onCreateTapped) {
      Label("일정 추가", systemImage: "plus")
        .font(.caption.weight(.bold))
        .foregroundStyle(.white)
        .lineLimit(1)
        .minimumScaleFactor(0.8)
        .frame(width: 86, height: 36)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color.calioBrand))
    }
    .buttonStyle(.plain)
    .accessibilityLabel("일정 추가")
    .accessibilityIdentifier("calendar_navigation_add_event")
  }

  private var hasVoteActions: Bool {
    onCreateVoteTapped != nil || onMyVotesTapped != nil
  }

  private var voteActions: some View {
    HStack(spacing: 6) {
      if onCreateVoteTapped != nil {
        voteButton
      }

      if onMyVotesTapped != nil {
        myVotesButton
      }
    }
  }

  private var voteButton: some View {
    Button(action: { onCreateVoteTapped?() }) {
      Text("투표 만들기")
        .font(.caption.weight(.bold))
        .foregroundStyle(.white)
        .lineLimit(1)
        .minimumScaleFactor(0.85)
        .frame(width: 86, height: 36)
        .background(VotePrimaryActionStyle.gradient, in: RoundedRectangle(cornerRadius: 10))
    }
    .buttonStyle(.plain)
    .accessibilityLabel("투표 만들기")
    .accessibilityIdentifier("calendar_navigation_create_vote")
  }

  private var myVotesButton: some View {
    Button(action: { onMyVotesTapped?() }) {
      Text("내가 만든 투표")
        .font(.caption.weight(.bold))
        .foregroundStyle(.white)
        .lineLimit(1)
        .minimumScaleFactor(0.75)
        .frame(width: 86, height: 36)
        .background(VotePrimaryActionStyle.gradient, in: RoundedRectangle(cornerRadius: 10))
    }
    .buttonStyle(.plain)
    .accessibilityLabel("내 투표")
    .accessibilityIdentifier("calendar_navigation_my_votes")
  }
}

#Preview {
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
}
