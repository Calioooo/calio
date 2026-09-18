import Foundation

enum VoteListLoadState: Equatable {
  case idle
  case loading
  case loaded([VoteRoom])
  case failed
}

@MainActor
final class VoteListViewModel: ObservableObject {
  @Published private(set) var createdRoomState: VoteListLoadState

  private let voteService: VoteService

  init(voteService: VoteService = VoteService(), createdRoomState: VoteListLoadState = .idle) {
    self.voteService = voteService
    self.createdRoomState = createdRoomState
  }

  func loadCreatedRoomsIfNeeded() async {
    guard createdRoomState == .idle else {
      return
    }
    await loadCreatedRooms()
  }

  func reloadCreatedRooms() async {
    await loadCreatedRooms()
  }

  private func loadCreatedRooms() async {
    createdRoomState = .loading

    do {
      createdRoomState = .loaded(try await voteService.fetchMyCreatedRooms())
    } catch is CancellationError {
      createdRoomState = .idle
    } catch {
      createdRoomState = .failed
    }
  }
}
