import Foundation

enum CreatedVoteRoomsLoadState: Equatable {
  case idle
  case loading
  case loaded([VoteRoom])
  case failed
}

enum ParticipatedVoteRoomsLoadState: Equatable {
  case idle
  case loading
  case loaded([ParticipatedVoteRoom])
  case failed
}

@MainActor
final class VoteListViewModel: ObservableObject {
  @Published private(set) var createdRoomState: CreatedVoteRoomsLoadState
  @Published private(set) var participatedRoomState: ParticipatedVoteRoomsLoadState

  private let voteService: VoteService

  init(
    voteService: VoteService = VoteService(),
    createdRoomState: CreatedVoteRoomsLoadState = .idle,
    participatedRoomState: ParticipatedVoteRoomsLoadState = .idle
  ) {
    self.voteService = voteService
    self.createdRoomState = createdRoomState
    self.participatedRoomState = participatedRoomState
  }

  func loadIfNeeded() async {
    async let created: Void = loadCreatedRoomsIfNeeded()
    async let participated: Void = loadParticipatedRoomsIfNeeded()
    _ = await (created, participated)
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

  func loadParticipatedRoomsIfNeeded() async {
    guard participatedRoomState == .idle else {
      return
    }
    await loadParticipatedRooms()
  }

  func reloadParticipatedRooms() async {
    await loadParticipatedRooms()
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

  private func loadParticipatedRooms() async {
    participatedRoomState = .loading

    do {
      participatedRoomState = .loaded(try await voteService.fetchMyParticipatedRooms())
    } catch is CancellationError {
      participatedRoomState = .idle
    } catch {
      participatedRoomState = .failed
    }
  }
}
