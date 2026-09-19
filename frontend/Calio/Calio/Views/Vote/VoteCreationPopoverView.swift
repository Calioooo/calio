import SwiftUI

@MainActor
struct VoteCreationPopoverView: View {
  @StateObject private var viewModel: VoteCreationViewModel
  @State private var creationTask: Task<Void, Never>?

  let onDismiss: () -> Void
  let onCreated: (VoteRoom) -> Void

  init(
    onDismiss: @escaping () -> Void,
    onCreated: @escaping (VoteRoom) -> Void
  ) {
    _viewModel = StateObject(wrappedValue: VoteCreationViewModel())
    self.onDismiss = onDismiss
    self.onCreated = onCreated
  }

  init(
    viewModel: VoteCreationViewModel,
    onDismiss: @escaping () -> Void,
    onCreated: @escaping (VoteRoom) -> Void
  ) {
    _viewModel = StateObject(wrappedValue: viewModel)
    self.onDismiss = onDismiss
    self.onCreated = onCreated
  }

  var body: some View {
    VotePopoverBackdrop {
      ScrollView {
        VStack(alignment: .leading, spacing: 22) {
          header
          nameInput
          candidatePeriod
          creationButton
        }
        .padding(26)
      }
      .scrollIndicators(.hidden)
      .frame(maxWidth: 560)
      .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 28))
      .overlay(RoundedRectangle(cornerRadius: 28).stroke(Color.calioDivider, lineWidth: 1))
      .shadow(color: .black.opacity(0.2), radius: 24, y: 12)
      .padding(16)
    }
    .accessibilityIdentifier("vote_creation_popover")
  }

  private var header: some View {
    HStack(alignment: .top) {
      Text("투표 만들기")
        .font(.title.bold())
        .foregroundStyle(.calioPrimary)
      Spacer()
      Button(action: dismissCreation) {
        Image(systemName: "xmark")
          .font(.headline.weight(.semibold))
          .foregroundStyle(.calioTextSecondary)
          .frame(width: 44, height: 44)
          .background(Color.voteAccentSoft, in: Circle())
      }
      .buttonStyle(.plain)
      .accessibilityLabel("투표 만들기 닫기")
    }
  }

  private var nameInput: some View {
    VStack(alignment: .leading, spacing: 10) {
      Text("투표방 이름")
        .font(.headline.weight(.semibold))
        .foregroundStyle(.calioPrimary)
      TextField(
        "투표방 이름을 입력하세요.",
        text: Binding(get: { viewModel.name }, set: { viewModel.updateName($0) })
      )
      .textInputAutocapitalization(.sentences)
      .padding(.horizontal, 16)
      .frame(minHeight: 54)
      .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 14))
      .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.calioDivider, lineWidth: 1))
      .accessibilityIdentifier("vote_creation_name")
    }
  }

  private var candidatePeriod: some View {
    VStack(alignment: .leading, spacing: 10) {
      Text("후보 기간")
        .font(.headline.weight(.semibold))
        .foregroundStyle(.calioPrimary)
      VoteMonthCalendarView(
        month: viewModel.displayedMonth,
        candidatePeriod: viewModel.candidatePeriod,
        selectedDay: viewModel.selectedCandidateEndDay,
        onMonthChange: { viewModel.moveMonth(by: $0) },
        onSelectDay: { viewModel.selectCandidateEndDay($0) }
      )
      periodSummary
    }
  }

  private var periodSummary: some View {
    HStack(spacing: 6) {
      Image(systemName: "calendar")
        .foregroundStyle(.voteAccent)
      Text("\(dayText(viewModel.selectedCandidateEndDay))까지")
        .font(.caption.weight(.semibold))
        .foregroundStyle(.calioPrimary)
        .lineLimit(1)
        .minimumScaleFactor(0.8)
      Divider()
        .frame(height: 24)
      Text("오늘부터 \(viewModel.selectedDayCount)일 동안")
        .font(.caption)
        .foregroundStyle(.calioTextSecondary)
        .lineLimit(1)
        .minimumScaleFactor(0.8)
      Spacer(minLength: 0)
    }
    .padding(.horizontal, 16)
    .frame(minHeight: 58)
    .background(Color.voteAccentSoft, in: RoundedRectangle(cornerRadius: 14))
  }

  private var creationButton: some View {
    VStack(spacing: 10) {
      if case .failed(let failure) = viewModel.state {
        Text(failure.message)
          .font(.footnote)
          .foregroundStyle(Color.calendarHoliday)
          .frame(maxWidth: .infinity, alignment: .leading)
      }
      Button {
        createVoteRoom()
      } label: {
        Group {
          if viewModel.state.isCreating {
            ProgressView()
              .tint(.white)
          } else {
            Text("투표 생성")
              .font(.headline.weight(.semibold))
          }
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, minHeight: 54)
        .background(VotePrimaryActionStyle.gradient, in: RoundedRectangle(cornerRadius: 14))
      }
      .buttonStyle(.plain)
      .disabled(!viewModel.canCreate)
      .opacity(viewModel.canCreate ? 1 : 0.45)
      .accessibilityIdentifier("vote_creation_submit")
    }
  }

  private func dayText(_ day: VoteDay) -> String {
    "\(String(day.year))년 \(day.month)월 \(day.day)일"
  }

  private func createVoteRoom() {
    guard creationTask == nil else {
      return
    }

    creationTask = Task {
      defer { creationTask = nil }

      guard let room = await viewModel.createRoom(), !Task.isCancelled else {
        return
      }
      onCreated(room)
    }
  }

  private func dismissCreation() {
    creationTask?.cancel()
    creationTask = nil
    onDismiss()
  }
}

private struct VoteMonthCalendarView: View {
  let month: VoteMonth
  let candidatePeriod: VoteCandidatePeriod
  let selectedDay: VoteDay
  let onMonthChange: (Int) -> Void
  let onSelectDay: (VoteDay) -> Void

  private let weekdaySymbols = ["일", "월", "화", "수", "목", "금", "토"]
  private let columns = Array(repeating: GridItem(.flexible(), spacing: 4), count: 7)

  var body: some View {
    VStack(spacing: 16) {
      HStack {
        monthButton(symbol: "chevron.left", direction: -1)
        Spacer()
        HStack(spacing: 8) {
          Text("\(String(month.year))년 \(month.month)월")
            .font(.headline.weight(.semibold))
          Image(systemName: "chevron.down")
            .font(.caption.weight(.bold))
        }
        .foregroundStyle(.calioPrimary)
        Spacer()
        monthButton(symbol: "chevron.right", direction: 1)
      }
      LazyVGrid(columns: columns, spacing: 8) {
        ForEach(weekdaySymbols, id: \.self) { symbol in
          Text(symbol)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.calioTextSecondary)
            .frame(height: 28)
        }
        ForEach(Array(days.enumerated()), id: \.offset) { _, day in
          if let day {
            dayButton(day)
          } else {
            Color.clear.frame(height: 38)
          }
        }
      }
    }
    .padding(16)
    .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 16))
    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.calioDivider, lineWidth: 1))
    .gesture(
      DragGesture(minimumDistance: 30)
        .onEnded { value in
          guard abs(value.translation.width) > abs(value.translation.height) else { return }
          onMonthChange(value.translation.width < 0 ? 1 : -1)
        }
    )
    .accessibilityIdentifier("vote_creation_calendar")
  }

  private var days: [VoteDay?] {
    guard let firstDate = calendar.date(from: DateComponents(year: month.year, month: month.month)),
      let range = calendar.range(of: .day, in: .month, for: firstDate)
    else {
      return []
    }
    let leadingEmptyDays = calendar.component(.weekday, from: firstDate) - 1
    return Array(repeating: nil, count: leadingEmptyDays)
      + range.map { VoteDay(year: month.year, month: month.month, day: $0) }
  }

  private var calendar: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
    return calendar
  }

  private func monthButton(symbol: String, direction: Int) -> some View {
    Button {
      onMonthChange(direction)
    } label: {
      Image(systemName: symbol)
        .font(.subheadline.weight(.bold))
        .foregroundStyle(.calioPrimary)
        .frame(width: 38, height: 38)
        .background(Color.voteAccentSoft, in: Circle())
    }
    .buttonStyle(.plain)
    .accessibilityLabel(direction < 0 ? "이전 달" : "다음 달")
  }

  private func dayButton(_ day: VoteDay) -> some View {
    let isSelectable = candidatePeriod.contains(day)
    let isSelected = selectedDay == day
    return Button {
      onSelectDay(day)
    } label: {
      Text("\(day.day)")
        .font(.subheadline.weight(isSelected ? .bold : .regular))
        .foregroundStyle(isSelected ? .white : isSelectable ? .calioPrimary : .calioTextSecondary)
        .frame(width: 38, height: 38)
        .background(isSelected ? Color.voteAccent : .clear, in: Circle())
        .opacity(isSelectable ? 1 : 0.35)
    }
    .buttonStyle(.plain)
    .disabled(!isSelectable)
    .accessibilityLabel("\(day.month)월 \(day.day)일")
  }
}

struct VotePopoverBackdrop<Content: View>: View {
  let content: Content

  init(@ViewBuilder content: () -> Content) {
    self.content = content()
  }

  var body: some View {
    ZStack {
      Rectangle()
        .fill(.ultraThinMaterial)
        .overlay(Color.calioPrimary.opacity(0.18))
        .ignoresSafeArea()
      content
    }
  }
}

#Preview {
  VoteCreationPopoverView(onDismiss: {}, onCreated: { _ in })
}
