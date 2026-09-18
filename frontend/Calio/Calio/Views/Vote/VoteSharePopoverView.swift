import SwiftUI
import UIKit

struct VoteSharePopoverView: View {
  let room: VoteRoom
  let onOpenRoom: (VoteRoom) -> Void

  @State private var didCopyLink = false

  var body: some View {
    VotePopoverBackdrop {
      VStack(spacing: 24) {
        Image(systemName: "link")
          .font(.system(size: 24, weight: .semibold))
          .foregroundStyle(.white)
          .frame(width: 58, height: 58)
          .background(VotePrimaryActionStyle.gradient, in: Circle())

        VStack(spacing: 8) {
          Text("투표가 만들어졌어요")
            .font(.title2.bold())
            .foregroundStyle(.calioPrimary)
          Text("링크를 공유해 멤버를 초대해보세요.")
            .font(.body)
            .foregroundStyle(.calioTextSecondary)
        }

        Text(room.name)
          .font(.headline.weight(.semibold))
          .foregroundStyle(.calioPrimary)
          .frame(maxWidth: .infinity, alignment: .leading)
          .padding(18)
          .background(Color.voteAccentSoft, in: RoundedRectangle(cornerRadius: 16))

        VStack(alignment: .leading, spacing: 10) {
          Text("공유 링크")
            .font(.headline.weight(.semibold))
            .foregroundStyle(.calioPrimary)
          HStack(spacing: 8) {
            Text(publicLink.absoluteString)
              .font(.subheadline)
              .foregroundStyle(.calioPrimary)
              .lineLimit(1)
              .truncationMode(.middle)
            Spacer(minLength: 0)
            Divider()
              .frame(height: 28)
            Button(didCopyLink ? "복사됨" : "복사") {
              UIPasteboard.general.url = publicLink
              didCopyLink = true
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.voteAccent)
            .padding(.horizontal, 12)
            .frame(minHeight: 36)
            .background(Color.voteAccentSoft, in: Capsule())
            .accessibilityIdentifier("vote_share_copy")
          }
          .padding(.leading, 16)
          .padding(.trailing, 8)
          .frame(minHeight: 58)
          .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 14))
          .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.calioDivider, lineWidth: 1))
        }

        ShareLink(item: publicLink) {
          Label("링크 공유하기", systemImage: "square.and.arrow.up")
            .font(.headline.weight(.semibold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 54)
            .background(VotePrimaryActionStyle.gradient, in: RoundedRectangle(cornerRadius: 14))
        }
        .accessibilityIdentifier("vote_share_system")

        Button("투표방으로 이동") { onOpenRoom(room) }
          .font(.headline.weight(.semibold))
          .foregroundStyle(.voteAccent)
          .frame(minHeight: 44)
          .accessibilityIdentifier("vote_share_open_room")
      }
      .padding(28)
      .frame(maxWidth: 560)
      .background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 28))
      .overlay(alignment: .topTrailing) {
        Button {
          onOpenRoom(room)
        } label: {
          Image(systemName: "xmark")
            .font(.headline.weight(.semibold))
            .foregroundStyle(.calioTextSecondary)
            .frame(width: 44, height: 44)
            .background(Color.voteAccentSoft, in: Circle())
        }
        .buttonStyle(.plain)
        .padding(18)
        .accessibilityLabel("공유 닫기")
      }
      .shadow(color: .black.opacity(0.2), radius: 24, y: 12)
      .padding(16)
    }
    .accessibilityIdentifier("vote_share_popover")
  }

  private var publicLink: URL {
    CalioAPIConfig.baseURL
      .appendingPathComponent("vote-rooms")
      .appendingPathComponent(room.publicId.uuidString)
  }
}

#Preview {
  VoteSharePopoverView(
    room: VoteRoom(
      publicId: UUID(),
      name: "가을 여행 일정",
      candidateStartDay: VoteDay(year: 2026, month: 9, day: 18),
      candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
    ),
    onOpenRoom: { _ in }
  )
}
