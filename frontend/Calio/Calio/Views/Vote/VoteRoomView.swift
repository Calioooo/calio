import SwiftUI

struct VoteRoomView: View {
  @Environment(\.scenePhase) private var scenePhase
  @Environment(\.dismiss) private var dismiss
  @StateObject private var viewModel: VoteRoomViewModel
  @State private var displayedMonth: VoteMonth
  @State private var resultDayForPopover: VoteDateResult?
  @State private var showsLeaveConfirmation = false

  let onClose: () -> Void

  init(room: VoteRoom, onClose: @escaping () -> Void) {
    _viewModel = StateObject(wrappedValue: VoteRoomViewModel(room: room))
    _displayedMonth = State(initialValue: VoteMonth(day: room.candidateStartDay))
    self.onClose = onClose
  }

  init(publicId: UUID, onClose: @escaping () -> Void) {
    _viewModel = StateObject(wrappedValue: VoteRoomViewModel(publicId: publicId))
    _displayedMonth = State(initialValue: VoteMonth(day: VoteDay(year: 2026, month: 1, day: 1)))
    self.onClose = onClose
  }

  init(viewModel: VoteRoomViewModel, onClose: @escaping () -> Void) {
    _viewModel = StateObject(wrappedValue: viewModel)
    _displayedMonth = State(
      initialValue: VoteMonth(
        day: viewModel.room?.candidateStartDay ?? VoteDay(year: 2026, month: 1, day: 1)))
    self.onClose = onClose
  }

  var body: some View {
    Group {
      switch viewModel.loadState {
      case .loading:
        ProgressView("투표방을 불러오는 중")
      case .unavailable:
        unavailableView
      case .failed(let failure):
        failedView(failure)
      case .loaded:
        roomContent
      }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.calioBackground.ignoresSafeArea())
    .task {
      await viewModel.load()
      if scenePhase == .active { viewModel.startPolling() }
    }
    .onChange(of: scenePhase) { _, phase in
      if phase == .active {
        viewModel.startPolling()
      } else {
        viewModel.stopPolling()
      }
    }
    .onDisappear { viewModel.stopPolling() }
    .alert("저장하지 않은 변경 사항", isPresented: $showsLeaveConfirmation) {
      Button("계속 편집", role: .cancel) {}
      Button("저장하지 않고 나가기", role: .destructive) { closeRoom() }
    } message: {
      Text("저장하지 않은 날짜 선택이 있습니다.")
    }
    .alert(
      "내 일정 불러오기",
      isPresented: Binding(
        get: { viewModel.needsScheduleReloadConfirmation },
        set: { isPresented in
          if !isPresented { viewModel.cancelPersonalScheduleReload() }
        }
      )
    ) {
      Button("취소", role: .cancel) { viewModel.cancelPersonalScheduleReload() }
      Button("다시 불러오기") { Task { await viewModel.confirmPersonalScheduleReload() } }
    } message: {
      Text("내 일정으로 다시 불러오면 현재 선택이 개인 일정 기준으로 바뀝니다.")
    }
  }

  private var roomContent: some View {
    VStack(spacing: 0) {
      navigationBar
      switch viewModel.participantFlow {
      case .result:
        resultContent
      case .existingParticipant, .newParticipant:
        participantCredentials
      case .editing:
        editingContent
      }
    }
  }

  private var navigationBar: some View {
    HStack {
      Button(action: requestClose) {
        Image(systemName: "chevron.left")
          .font(.title3.weight(.semibold))
          .frame(width: 44, height: 44)
      }
      .buttonStyle(.plain)
      .foregroundStyle(.calioPrimary)
      .accessibilityLabel("투표방 닫기")
      Spacer()
      Text(viewModel.room?.name ?? "투표방")
        .font(.title2.bold())
        .foregroundStyle(.calioPrimary)
        .lineLimit(1)
      Spacer()
      Color.clear.frame(width: 44, height: 44)
    }
    .padding(.horizontal, 18)
    .padding(.top, 8)
  }

  private var resultContent: some View {
    VStack(spacing: 24) {
      Text("색이 진할수록 참여가 어려운 멤버가 많은 날이에요.")
        .font(.body)
        .foregroundStyle(.calioTextSecondary)
        .multilineTextAlignment(.center)
        .padding(.horizontal, 24)

      if let room = viewModel.room {
        VoteRoomCalendarGrid(
          room: room,
          month: displayedMonth,
          selectedDays: [],
          dateResults: Dictionary(
            uniqueKeysWithValues: (viewModel.result?.dateResults ?? []).map { ($0.day, $0) }),
          isEditing: false,
          onMonthChange: moveMonth(by:),
          onDayTap: { day in
            guard let result = viewModel.result?.dateResults.first(where: { $0.day == day }),
              result.unavailableCount > 0
            else { return }
            resultDayForPopover = result
          }
        )
        .popover(item: $resultDayForPopover, arrowEdge: .top) { result in
          VoteResultNicknamePopover(result: result)
            .presentationCompactAdaptation(.popover)
        }
      }
      Spacer(minLength: 0)
      Button("투표 참여하기") { viewModel.showExistingParticipant() }
        .buttonStyle(VoteRoomPrimaryButtonStyle())
        .padding(.horizontal, 36)
        .padding(.bottom, 28)
        .accessibilityIdentifier("vote_room_join")
    }
  }

  private var participantCredentials: some View {
    VStack(spacing: 20) {
      Spacer(minLength: 36)
      Image(systemName: "person.crop.circle.badge.checkmark")
        .font(.system(size: 48))
        .foregroundStyle(.voteAccent)
      Text(viewModel.participantFlow == .newParticipant ? "처음 투표하시나요?" : "투표에 참여하기")
        .font(.title2.bold())
        .foregroundStyle(.calioPrimary)
      Text(
        viewModel.participantFlow == .newParticipant ? "닉네임과 비밀번호를 등록해주세요." : "기존 참여 정보를 입력해주세요."
      )
      .font(.body)
      .foregroundStyle(.calioTextSecondary)

      VStack(spacing: 12) {
        TextField("닉네임", text: $viewModel.nickname)
          .textInputAutocapitalization(.never)
          .padding(.horizontal, 16)
          .frame(height: 54)
          .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 14))
          .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.calioDivider, lineWidth: 1))
        SecureField("비밀번호", text: $viewModel.password)
          .padding(.horizontal, 16)
          .frame(height: 54)
          .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 14))
          .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.calioDivider, lineWidth: 1))
      }
      .padding(.horizontal, 36)

      if let failure = viewModel.actionFailure {
        Text(failure.message)
          .font(.footnote)
          .foregroundStyle(Color.calendarHoliday)
          .padding(.horizontal, 36)
      }

      Button(viewModel.participantFlow == .newParticipant ? "투표하기" : "투표하기") {
        Task {
          if viewModel.participantFlow == .newParticipant {
            await viewModel.registerParticipant()
          } else {
            await viewModel.restoreParticipantSelection()
          }
        }
      }
      .buttonStyle(VoteRoomPrimaryButtonStyle())
      .disabled(!viewModel.canSubmitCredentials)
      .opacity(viewModel.canSubmitCredentials ? 1 : 0.45)
      .padding(.horizontal, 36)

      Button(viewModel.participantFlow == .newParticipant ? "이미 참여하셨나요?" : "처음 투표하시는 건가요?") {
        if viewModel.participantFlow == .newParticipant {
          viewModel.showExistingParticipant()
        } else {
          viewModel.showNewParticipant()
        }
      }
      .font(.footnote.weight(.semibold))
      .foregroundStyle(.voteAccent)
      .underline()
      Button("취소") { viewModel.cancelParticipantFlow() }
        .font(.footnote)
        .foregroundStyle(.calioTextSecondary)
      Spacer()
    }
  }

  private var editingContent: some View {
    VStack(spacing: 18) {
      Text("변경 사항은 저장 후 참여자에게 반영됩니다.")
        .font(.body)
        .foregroundStyle(.calioTextSecondary)
        .padding(.top, 8)

      Button {
        Task { await viewModel.requestPersonalSchedule() }
      } label: {
        HStack(spacing: 14) {
          Image(systemName: "calendar")
            .font(.title3.weight(.semibold))
          Text(viewModel.isLoadingSchedule ? "일정을 불러오는 중" : "내 일정 불러오기")
            .font(.headline.weight(.semibold))
          Spacer()
          Image(systemName: "chevron.right")
        }
        .foregroundStyle(.calioPrimary)
        .padding(.horizontal, 20)
        .frame(height: 68)
        .background(Color.voteAccentSoft, in: RoundedRectangle(cornerRadius: 18))
        .overlay(
          RoundedRectangle(cornerRadius: 18).stroke(Color.voteAccent.opacity(0.15), lineWidth: 1))
      }
      .buttonStyle(.plain)
      .padding(.horizontal, 36)

      if let failure = viewModel.actionFailure {
        Text(failure == .network ? "일정을 불러오지 못했습니다. 날짜를 직접 선택해주세요." : failure.message)
          .font(.footnote)
          .foregroundStyle(Color.calendarHoliday)
          .padding(.horizontal, 36)
      }

      if let room = viewModel.room {
        VoteRoomCalendarGrid(
          room: room,
          month: displayedMonth,
          selectedDays: viewModel.draftUnavailableDays,
          dateResults: [:],
          isEditing: true,
          onMonthChange: moveMonth(by:),
          onDayTap: viewModel.toggleUnavailableDay
        )
      }
      Spacer(minLength: 0)
      if viewModel.didSave {
        Label("저장됨", systemImage: "checkmark.circle.fill")
          .font(.footnote.weight(.semibold))
          .foregroundStyle(.voteAccent)
      }
      Button(viewModel.isSubmitting ? "저장하는 중" : "저장") {
        Task { await viewModel.submitVotes() }
      }
      .buttonStyle(VoteRoomPrimaryButtonStyle())
      .disabled(viewModel.isSubmitting)
      .padding(.horizontal, 36)
      .padding(.bottom, 28)
      .accessibilityIdentifier("vote_room_save")
    }
  }

  private var unavailableView: some View {
    VStack(spacing: 14) {
      Image(systemName: "link.badge.minus")
        .font(.system(size: 42))
        .foregroundStyle(.calioTextSecondary)
      Text("더 이상 사용할 수 없는 투표방입니다")
        .font(.title3.bold())
        .foregroundStyle(.calioPrimary)
      Text("투표방이 삭제되었거나 링크가 만료되었습니다.")
        .font(.body)
        .foregroundStyle(.calioTextSecondary)
      Button("Calio 홈으로 돌아가기", action: closeRoom)
        .buttonStyle(VoteRoomPrimaryButtonStyle())
        .padding(.horizontal, 36)
    }
  }

  private func failedView(_ failure: VoteRoomFailure) -> some View {
    VStack(spacing: 14) {
      Text(failure.message)
        .foregroundStyle(.calioPrimary)
      Button("다시 시도") { Task { await viewModel.load() } }
        .buttonStyle(.borderedProminent)
    }
  }

  private func requestClose() {
    if viewModel.hasUnsavedChanges {
      showsLeaveConfirmation = true
    } else {
      closeRoom()
    }
  }

  private func closeRoom() {
    onClose()
    dismiss()
  }

  private func moveMonth(by value: Int) {
    let calendar = Calendar.voteKorea
    guard
      let date = calendar.date(
        from: DateComponents(year: displayedMonth.year, month: displayedMonth.month)),
      let moved = calendar.date(byAdding: .month, value: value, to: date)
    else { return }
    displayedMonth = VoteMonth(day: VoteDay(date: moved, calendar: calendar))
  }
}

private struct VoteRoomCalendarGrid: View {
  let room: VoteRoom
  let month: VoteMonth
  let selectedDays: Set<VoteDay>
  let dateResults: [VoteDay: VoteDateResult]
  let isEditing: Bool
  let onMonthChange: (Int) -> Void
  let onDayTap: (VoteDay) -> Void

  private let columns = Array(repeating: GridItem(.flexible(), spacing: 8), count: 7)
  private let weekdays = ["일", "월", "화", "수", "목", "금", "토"]

  var body: some View {
    VStack(spacing: 16) {
      HStack {
        monthButton("chevron.left", -1)
        Spacer()
        Text(verbatim: "\(month.year)년 \(month.month)월")
          .font(.title3.bold())
          .foregroundStyle(.calioPrimary)
        Spacer()
        monthButton("chevron.right", 1)
      }
      LazyVGrid(columns: columns, spacing: 10) {
        ForEach(weekdays, id: \.self) { weekday in
          Text(weekday)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(.calioTextSecondary)
            .frame(height: 30)
        }
        ForEach(Array(days.enumerated()), id: \.offset) { _, day in
          if let day {
            dayButton(day)
          } else {
            Color.clear.frame(height: 58)
          }
        }
      }
      .frame(maxWidth: .infinity)
    }
    .frame(maxWidth: .infinity)
    .padding(20)
    .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 26))
    .shadow(color: .black.opacity(0.06), radius: 16, y: 8)
    .padding(.horizontal, 28)
    .gesture(
      DragGesture(minimumDistance: 30).onEnded { value in
        guard abs(value.translation.width) > abs(value.translation.height) else { return }
        onMonthChange(value.translation.width < 0 ? 1 : -1)
      }
    )
  }

  private var days: [VoteDay?] {
    let calendar = Calendar.voteKorea
    guard
      let first = calendar.date(from: DateComponents(year: month.year, month: month.month)),
      let range = calendar.range(of: .day, in: .month, for: first)
    else { return [] }
    return Array(repeating: nil, count: calendar.component(.weekday, from: first) - 1)
      + range.map { VoteDay(year: month.year, month: month.month, day: $0) }
  }

  private func monthButton(_ symbol: String, _ direction: Int) -> some View {
    Button {
      onMonthChange(direction)
    } label: {
      Image(systemName: symbol)
        .font(.headline.weight(.bold))
        .foregroundStyle(.calioPrimary)
        .frame(width: 42, height: 42)
    }
    .buttonStyle(.plain)
  }

  private func dayButton(_ day: VoteDay) -> some View {
    let isCandidateDay = VoteRoomCalendar.days(in: room).contains(day)
    let result = dateResults[day]
    return Button {
      if isCandidateDay { onDayTap(day) }
    } label: {
      ZStack(alignment: .topTrailing) {
        Text("\(day.day)")
          .font(.body.weight(.medium))
          .foregroundStyle(isCandidateDay ? .calioPrimary : .calioTextSecondary)
        if isEditing && selectedDays.contains(day) {
          Image(systemName: "checkmark.circle.fill")
            .font(.caption)
            .foregroundStyle(.white)
            .padding(7)
        }
      }
      .frame(maxWidth: .infinity, minHeight: 58)
      .background(backgroundColor(for: day, result: result), in: RoundedRectangle(cornerRadius: 14))
      .overlay(
        RoundedRectangle(cornerRadius: 14)
          .stroke(
            isEditing && selectedDays.contains(day)
              ? Color.voteAccent : Color.calioDivider.opacity(0.65), lineWidth: 1)
      )
      .opacity(isCandidateDay ? 1 : 0.34)
    }
    .buttonStyle(.plain)
    .disabled(!isCandidateDay)
    .accessibilityLabel(accessibilityLabel(for: day, result: result))
  }

  private func backgroundColor(for day: VoteDay, result: VoteDateResult?) -> Color {
    if isEditing, selectedDays.contains(day) { return Color.voteAccentSoft }
    guard let result, result.unavailableCount > 0 else { return Color.calioSurface }
    let maximum = dateResults.values.map(\.unavailableCount).max() ?? result.unavailableCount
    let intensity = 0.16 + 0.42 * Double(result.unavailableCount) / Double(max(maximum, 1))
    return Color.calendarHoliday.opacity(intensity)
  }

  private func accessibilityLabel(for day: VoteDay, result: VoteDateResult?) -> String {
    let date = "\(day.month)월 \(day.day)일"
    guard let result, result.unavailableCount > 0 else { return date }
    return "\(date), 참여가 어려운 멤버 \(result.unavailableCount)명"
  }
}

private struct VoteResultNicknamePopover: View {
  let result: VoteDateResult

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      Text("이 날 참여가 어려운 멤버")
        .font(.footnote)
        .foregroundStyle(.calioTextSecondary)
      Divider()
      ForEach(result.unavailableNicknames, id: \.self) { nickname in
        Label(nickname, systemImage: "person.circle.fill")
          .foregroundStyle(.calioPrimary)
      }
    }
    .padding(18)
    .frame(minWidth: 210)
  }
}

private struct VoteRoomPrimaryButtonStyle: ButtonStyle {
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .font(.headline.weight(.semibold))
      .foregroundStyle(.white)
      .frame(maxWidth: .infinity, minHeight: 58)
      .background(VotePrimaryActionStyle.gradient, in: RoundedRectangle(cornerRadius: 18))
      .opacity(configuration.isPressed ? 0.82 : 1)
  }
}

#Preview {
  VoteRoomView(
    room: VoteRoom(
      publicId: UUID(), name: "가을 여행 일정",
      candidateStartDay: VoteDay(year: 2026, month: 10, day: 1),
      candidateEndDay: VoteDay(year: 2026, month: 10, day: 31)
    ),
    onClose: {}
  )
}
