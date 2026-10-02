import SwiftUI

@MainActor
struct VoteListView: View {
  private enum Tab: CaseIterable, Hashable {
    case created
    case participated

    var title: String {
      switch self {
      case .created: "만든 투표"
      case .participated: "참여한 투표"
      }
    }

    var emptyMessage: String {
      switch self {
      case .created: "만든 투표가 없어요."
      case .participated: "참여한 투표가 없어요."
      }
    }
  }

  @StateObject private var viewModel: VoteListViewModel
  let onClose: () -> Void
  let onRoomSelected: (VoteRoom) -> Void
  let onCreateVote: () -> Void

  @State private var selectedTab: Tab = .created

  init(
    onClose: @escaping () -> Void,
    onRoomSelected: @escaping (VoteRoom) -> Void,
    onCreateVote: @escaping () -> Void
  ) {
    _viewModel = StateObject(wrappedValue: VoteListViewModel())
    self.onClose = onClose
    self.onRoomSelected = onRoomSelected
    self.onCreateVote = onCreateVote
  }

  init(
    viewModel: VoteListViewModel,
    onClose: @escaping () -> Void,
    onRoomSelected: @escaping (VoteRoom) -> Void,
    onCreateVote: @escaping () -> Void
  ) {
    _viewModel = StateObject(wrappedValue: viewModel)
    self.onClose = onClose
    self.onRoomSelected = onRoomSelected
    self.onCreateVote = onCreateVote
  }

  var body: some View {
    VStack(spacing: 0) {
      header
      ScrollView {
        VStack(alignment: .leading, spacing: 24) {
          Text("만든 투표와 참여한 투표를 확인해보세요.")
            .font(.body)
            .foregroundStyle(.calioTextSecondary)

          tabPicker
          roomList
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 28)
      }
      createVoteButton
    }
    .background(Color.calioBackground)
    .accessibilityIdentifier("vote_list")
    .task { await viewModel.loadIfNeeded() }
  }

  private var header: some View {
    HStack {
      Button(action: onClose) {
        Image(systemName: "chevron.left")
          .font(.title3.weight(.bold))
          .foregroundStyle(.calioPrimary)
          .frame(width: 44, height: 44)
      }
      .buttonStyle(.plain)
      .accessibilityLabel("내 투표 닫기")
      .accessibilityIdentifier("vote_list_close")

      Spacer()
      Text("내 투표")
        .font(.title2.bold())
        .foregroundStyle(.calioPrimary)
      Spacer()
      Color.clear.frame(width: 44, height: 44)
    }
    .padding(.horizontal, 16)
    .padding(.vertical, 10)
  }

  private var tabPicker: some View {
    HStack(spacing: 0) {
      ForEach(Tab.allCases, id: \.self) { tab in
        Button {
          selectedTab = tab
        } label: {
          Text("\(tab.title) \(roomCount(for: tab))")
            .font(.headline.weight(.semibold))
            .foregroundStyle(selectedTab == tab ? .calioAccent : .calioTextSecondary)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background {
              if selectedTab == tab {
                RoundedRectangle(cornerRadius: 16)
                  .fill(Color.calioSurface)
                  .shadow(color: .black.opacity(0.06), radius: 4, y: 2)
              }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("vote_list_tab_\(tab.title)")
      }
    }
    .padding(5)
    .background(Color.calioSelection, in: RoundedRectangle(cornerRadius: 20))
  }

  @ViewBuilder
  private var roomList: some View {
    switch selectedTab {
    case .created:
      createdRoomContent
    case .participated:
      participatedRoomContent
    }
  }

  @ViewBuilder
  private var createdRoomContent: some View {
    switch viewModel.createdRoomState {
    case .idle, .loading:
      loadingState
    case .loaded(let rooms):
      rooms.isEmpty ? AnyView(emptyState) : AnyView(createdRoomCards(rooms))
    case .failed(let failure):
      failedState(message: failure.message, onRetry: viewModel.reloadCreatedRooms)
    }
  }

  @ViewBuilder
  private var participatedRoomContent: some View {
    switch viewModel.participatedRoomState {
    case .idle, .loading:
      loadingState
    case .loaded(let rooms):
      rooms.isEmpty ? AnyView(emptyState) : AnyView(participatedRoomCards(rooms))
    case .failed:
      failedState(
        message: "투표를 불러오지 못했습니다.",
        onRetry: viewModel.reloadParticipatedRooms
      )
    }
  }

  private var loadingState: some View {
    ProgressView("투표를 불러오는 중")
      .frame(maxWidth: .infinity)
      .padding(.vertical, 56)
  }

  private func createdRoomCards(_ rooms: [VoteRoom]) -> some View {
    roomCardList {
      ForEach(rooms) { room in
        roomButton(room: room, relationship: "내가 만든 투표")
      }
    }
  }

  private func participatedRoomCards(_ rooms: [ParticipatedVoteRoom]) -> some View {
    roomCardList {
      ForEach(rooms) { participant in
        roomButton(
          room: participant.room,
          relationship: participantRelationshipText(participant),
          detail: participantDetailText(participant)
        )
      }
    }
  }

  private func roomCardList<Content: View>(@ViewBuilder content: () -> Content) -> some View {
    VStack(spacing: 14) {
      content()
    }
    .overlay(alignment: .bottomLeading) {
      Text("투표를 선택하면 투표방으로 이동합니다.")
        .font(.footnote)
        .foregroundStyle(.calioTextSecondary)
        .offset(y: 34)
    }
    .padding(.bottom, 34)
  }

  private func failedState(
    message: String,
    onRetry: @escaping () async -> Void
  ) -> some View {
    VStack(spacing: 12) {
      Text(message)
        .font(.headline)
        .foregroundStyle(.calioPrimary)
      Button("다시 시도") {
        Task { await onRetry() }
      }
      .font(.subheadline.weight(.semibold))
      .foregroundStyle(.calioAccent)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 56)
  }

  private func roomCount(for tab: Tab) -> Int {
    switch tab {
    case .created:
      if case .loaded(let rooms) = viewModel.createdRoomState { return rooms.count }
    case .participated:
      if case .loaded(let rooms) = viewModel.participatedRoomState { return rooms.count }
    }
    return 0
  }

  private var emptyState: some View {
    VStack(spacing: 10) {
      Image(systemName: "checklist")
        .font(.title2.weight(.semibold))
        .foregroundStyle(.calioAccent)
        .frame(width: 54, height: 54)
        .background(Color.calioSelection, in: RoundedRectangle(cornerRadius: 18))
      Text(selectedTab.emptyMessage)
        .font(.headline)
        .foregroundStyle(.calioPrimary)
      Text("투표를 만들거나 공유받은 링크로 참여해보세요.")
        .font(.footnote)
        .foregroundStyle(.calioTextSecondary)
        .multilineTextAlignment(.center)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 56)
  }

  private func roomButton(
    room: VoteRoom,
    relationship: String,
    detail: String? = nil
  ) -> some View {
    Button {
      onRoomSelected(room)
    } label: {
      HStack(spacing: 16) {
        Image(systemName: "calendar")
          .font(.title3.weight(.medium))
          .foregroundStyle(.calioAccent)
          .frame(width: 64, height: 64)
          .background(Color.calioSelection, in: RoundedRectangle(cornerRadius: 20))

        VStack(alignment: .leading, spacing: 6) {
          Text(room.name)
            .font(.headline.weight(.bold))
            .foregroundStyle(.calioPrimary)
            .lineLimit(1)
          Text(candidatePeriodText(for: room))
            .font(.subheadline)
            .foregroundStyle(.calioTextSecondary)
            .lineLimit(1)
          Text(relationship)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.calioAccent)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color.calioSelection, in: Capsule())
          if let detail {
            Text(detail)
              .font(.caption)
              .foregroundStyle(.calioTextSecondary)
          }
        }

        Spacer(minLength: 0)
        Image(systemName: "chevron.right")
          .font(.headline.weight(.bold))
          .foregroundStyle(.calioTextSecondary.opacity(0.7))
      }
      .padding(18)
      .frame(maxWidth: .infinity, alignment: .leading)
      .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 22))
      .overlay(RoundedRectangle(cornerRadius: 22).stroke(Color.calioDivider, lineWidth: 1))
    }
    .buttonStyle(.plain)
    .accessibilityLabel("\(room.name) 투표방 열기")
  }

  private var createVoteButton: some View {
    Button(action: onCreateVote) {
      Text("투표 만들기")
        .font(.headline.weight(.bold))
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, minHeight: 54)
        .background(VotePrimaryActionStyle.gradient, in: RoundedRectangle(cornerRadius: 16))
    }
    .buttonStyle(.plain)
    .padding(.horizontal, 20)
    .padding(.vertical, 16)
    .background(Color.calioBackground)
    .accessibilityIdentifier("vote_list_create")
  }

  private func participantRelationshipText(_ participant: ParticipatedVoteRoom) -> String {
    "\(participant.nickname)으로 참여"
  }

  private func participantDetailText(_ participant: ParticipatedVoteRoom) -> String {
    participant.participantStatus == .submitted ? "일정 선택 완료" : "일정 선택 전"
  }

  private func candidatePeriodText(for room: VoteRoom) -> String {
    "후보 기간 · \(dateText(room.candidateStartDay)) - \(dateText(room.candidateEndDay))"
  }

  private func dateText(_ day: VoteDay) -> String {
    "\(day.year). \(String(format: "%02d", day.month)). \(String(format: "%02d", day.day))"
  }
}

#Preview("내 투표") {
  VoteListView(
    viewModel: VoteListViewModel(
      createdRoomState: .loaded([
        VoteRoom(
          publicId: UUID(),
          name: "가을 여행 일정",
          candidateStartDay: VoteDay(year: 2026, month: 10, day: 1),
          candidateEndDay: VoteDay(year: 2026, month: 10, day: 31)
        )
      ]),
      participatedRoomState: .loaded([])
    ),
    onClose: {},
    onRoomSelected: { _ in },
    onCreateVote: {}
  )
}
