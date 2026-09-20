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
    HStack(spacing: 4) {
      CalendarYearMonthTitleView(
        referenceDay: referenceDay,
        onSelectedYearMonth: onSelectedYearMonth,
        titleFontSize: 18,
        minimumHitSize: 36
      )
      .layoutPriority(1)

      Spacer(minLength: 0)
      if showsTodayButton {
        compactTodayButton
      }
      compactGoogleCalendarButton
      voteActions
      compactCreateButton
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
        standardCreateButton
      }

      if let onCreateVoteTapped {
        accessibilityActionButton(
          title: "투표 만들기",
          accessibilityIdentifier: "calendar_navigation_create_vote",
          action: onCreateVoteTapped
        )
      }

      if let onMyVotesTapped {
        accessibilityActionButton(
          title: "내가 만든 투표",
          accessibilityIdentifier: "calendar_navigation_my_votes",
          action: onMyVotesTapped
        )
      }
    }
  }

  private var todayButton: some View {
    Button("오늘", action: onTodayTapped)
      .font(.subheadline.weight(.semibold))
      .foregroundStyle(.calioPrimary)
      .padding(.horizontal, 12)
      .frame(minHeight: 44)
      .background(RoundedRectangle(cornerRadius: 10).fill(Color.calioSelection))
      .accessibilityLabel("오늘로 이동")
      .accessibilityIdentifier("calendar_navigation_today")
  }

  private var googleCalendarButton: some View {
    Button(action: onGoogleCalendarConnectTapped) {
      Image(systemName: "calendar.badge.plus")
        .font(.system(size: 17, weight: .semibold))
        .foregroundStyle(.calioTextSecondary)
        .frame(width: 44, height: 44)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color.calioSurface))
    }
    .buttonStyle(.plain)
    .accessibilityLabel("Google Calendar 연동")
    .accessibilityHint("Google Calendar 인증을 시작합니다")
    .accessibilityIdentifier("calendar_navigation_google_connect")
  }

  private var compactGoogleCalendarButton: some View {
    Button(action: onGoogleCalendarConnectTapped) {
      Image(systemName: "calendar.badge.plus")
        .font(.system(size: 14, weight: .semibold))
        .foregroundStyle(.calioTextSecondary)
        .frame(width: 34, height: 34)
        .background(RoundedRectangle(cornerRadius: 9).fill(Color.calioSurface))
    }
    .buttonStyle(.plain)
    .accessibilityLabel("Google Calendar 연동")
    .accessibilityHint("Google Calendar 인증을 시작합니다")
    .accessibilityIdentifier("calendar_navigation_google_connect")
  }

  private var compactTodayButton: some View {
    Button("오늘", action: onTodayTapped)
      .font(.caption.weight(.semibold))
      .foregroundStyle(.calioPrimary)
      .lineLimit(1)
      .padding(.horizontal, 7)
      .frame(height: 34)
      .background(RoundedRectangle(cornerRadius: 9).fill(Color.calioSelection))
      .accessibilityLabel("오늘로 이동")
      .accessibilityIdentifier("calendar_navigation_today")
  }

  private var standardCreateButton: some View {
    Button(action: onCreateTapped) {
      Label("일정 추가", systemImage: "plus")
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(.white)
        .padding(.horizontal, 13)
        .frame(maxWidth: .infinity, minHeight: 44)
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
        .frame(width: 58, height: 34)
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
        .frame(width: 58, height: 34)
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
        .frame(width: 80, height: 34)
        .background(VotePrimaryActionStyle.gradient, in: RoundedRectangle(cornerRadius: 10))
    }
    .buttonStyle(.plain)
    .accessibilityLabel("내 투표")
    .accessibilityIdentifier("calendar_navigation_my_votes")
  }

  private func accessibilityActionButton(
    title: String,
    accessibilityIdentifier: String,
    action: @escaping () -> Void
  ) -> some View {
    Button(action: action) {
      Text(title)
        .font(.body.weight(.bold))
        .foregroundStyle(.white)
        .multilineTextAlignment(.center)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, minHeight: 44)
        .background(VotePrimaryActionStyle.gradient, in: RoundedRectangle(cornerRadius: 12))
    }
    .buttonStyle(.plain)
    .accessibilityLabel(title)
    .accessibilityIdentifier(accessibilityIdentifier)
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
