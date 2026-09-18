import SwiftUI

struct VoteListView: View {
  private enum Tab: CaseIterable, Hashable {
    case created
    case participated

    var title: String {
      switch self {
      case .created:
        return "만든 투표"
      case .participated:
        return "참여한 투표"
      }
    }

    var relationshipTitle: String {
      switch self {
      case .created:
        return "내가 만든 투표"
      case .participated:
        return "참여한 투표"
      }
    }

    var emptyMessage: String {
      switch self {
      case .created:
        return "만든 투표가 없어요."
      case .participated:
        return "참여한 투표가 없어요."
      }
    }
  }

  let createdRooms: [VoteRoom]
  let participatedRooms: [VoteRoom]
  let onClose: () -> Void
  let onRoomSelected: (VoteRoom) -> Void
  let onCreateVote: () -> Void

  @State private var selectedTab: Tab = .created

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
          Text("\(tab.title) \(rooms(for: tab).count)")
            .font(.headline.weight(.semibold))
            .foregroundStyle(selectedTab == tab ? .voteAccent : .calioTextSecondary)
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
    .background(Color.voteAccentSoft, in: RoundedRectangle(cornerRadius: 20))
  }

  @ViewBuilder
  private var roomList: some View {
    let rooms = rooms(for: selectedTab)
    if rooms.isEmpty {
      emptyState
    } else {
      VStack(spacing: 14) {
        ForEach(rooms) { room in
          roomButton(room)
        }
      }
      Text("투표를 선택하면 투표방으로 이동합니다.")
        .font(.footnote)
        .foregroundStyle(.calioTextSecondary)
    }
  }

  private var emptyState: some View {
    VStack(spacing: 10) {
      Image(systemName: "checklist")
        .font(.title2.weight(.semibold))
        .foregroundStyle(.voteAccent)
        .frame(width: 54, height: 54)
        .background(Color.voteAccentSoft, in: RoundedRectangle(cornerRadius: 18))
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

  private func roomButton(_ room: VoteRoom) -> some View {
    Button {
      onRoomSelected(room)
    } label: {
      HStack(spacing: 16) {
        Image(systemName: "calendar")
          .font(.title3.weight(.medium))
          .foregroundStyle(.voteAccent)
          .frame(width: 64, height: 64)
          .background(Color.voteAccentSoft, in: RoundedRectangle(cornerRadius: 20))

        VStack(alignment: .leading, spacing: 6) {
          Text(room.name)
            .font(.headline.weight(.bold))
            .foregroundStyle(.calioPrimary)
            .lineLimit(1)
          Text(candidatePeriodText(for: room))
            .font(.subheadline)
            .foregroundStyle(.calioTextSecondary)
            .lineLimit(1)
          Text(selectedTab.relationshipTitle)
            .font(.caption.weight(.semibold))
            .foregroundStyle(.voteAccent)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color.voteAccentSoft, in: Capsule())
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

  private func rooms(for tab: Tab) -> [VoteRoom] {
    switch tab {
    case .created:
      return createdRooms
    case .participated:
      return participatedRooms
    }
  }

  private func candidatePeriodText(for room: VoteRoom) -> String {
    "후보 기간 · \(startDateText(room.candidateStartDay)) - \(endDateText(for: room))"
  }

  private func startDateText(_ day: VoteDay) -> String {
    "\(day.year). \(String(format: "%02d", day.month)). \(String(format: "%02d", day.day))"
  }

  private func endDateText(for room: VoteRoom) -> String {
    let day = room.candidateEndDay
    if room.candidateStartDay.year == day.year {
      return "\(String(format: "%02d", day.month)). \(String(format: "%02d", day.day))"
    }
    return startDateText(day)
  }
}

#Preview("만든 투표") {
  VoteListView(
    createdRooms: [
      VoteRoom(
        publicId: UUID(),
        name: "가을 여행 일정",
        candidateStartDay: VoteDay(year: 2026, month: 10, day: 1),
        candidateEndDay: VoteDay(year: 2026, month: 10, day: 31)
      ),
      VoteRoom(
        publicId: UUID(),
        name: "팀 워크숍 날짜",
        candidateStartDay: VoteDay(year: 2026, month: 9, day: 21),
        candidateEndDay: VoteDay(year: 2026, month: 10, day: 12)
      ),
    ],
    participatedRooms: [],
    onClose: {},
    onRoomSelected: { _ in },
    onCreateVote: {}
  )
}
