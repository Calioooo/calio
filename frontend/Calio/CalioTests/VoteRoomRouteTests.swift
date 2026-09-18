import Foundation
import Testing

@testable import Calio

struct VoteRoomRouteTests {
  @Test func parsesPublicVoteRoomLink() {
    let publicId = UUID(uuidString: "A170EA5C-357C-4261-BD9F-9FCD31751398")!
    let url = CalioAPIConfig.baseURL
      .appendingPathComponent("vote-rooms")
      .appendingPathComponent(publicId.uuidString)

    let route = VoteRoomRoute.from(url: url)

    #expect(route?.publicId == publicId)
    #expect(route?.room == nil)
  }

  @Test func ignoresNonVotePublicLink() {
    #expect(VoteRoomRoute.from(url: CalioAPIConfig.baseURL.appendingPathComponent("groups")) == nil)
  }
}
