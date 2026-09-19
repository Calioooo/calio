import Foundation
import Testing

@testable import Calio

@Suite
struct VoteSharePopoverViewTests {
  @Test @MainActor
  func dismissAndOpenRoomActionsRemainIndependent() {
    let room = makeVoteRoom()
    var didDismiss = false
    var openedRoom: VoteRoom?
    let view = VoteSharePopoverView(
      room: room,
      onDismiss: { didDismiss = true },
      onOpenRoom: { openedRoom = $0 }
    )

    view.onDismiss()

    #expect(didDismiss)
    #expect(openedRoom == nil)

    view.onOpenRoom(room)

    #expect(didDismiss)
    #expect(openedRoom?.publicId == room.publicId)
  }

  @Test
  func publicLinkUsesCanonicalVoteRoomEndpoint() {
    let publicId = UUID(uuidString: "9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F")!
    let view = VoteSharePopoverView(
      room: makeVoteRoom(publicId: publicId),
      onOpenRoom: { _ in }
    )

    #expect(view.publicLink.path == "/api/vote-rooms/\(publicId.uuidString)")
  }
}

private func makeVoteRoom(publicId: UUID = UUID()) -> VoteRoom {
  VoteRoom(
    publicId: publicId,
    name: "가을 여행 일정",
    candidateStartDay: VoteDay(year: 2026, month: 9, day: 18),
    candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
  )
}
