import Foundation

enum CreatedVoteRoomsLoadState: Equatable {
  case idle
  case loading
  case loaded([VoteRoom])
  case failed(VoteListFailure)
}

enum VoteListFailure: Equatable {
  case network
  case decoding
  case backend
  case unexpected

  var message: String {
    switch self {
    case .network:
      return "네트워크 연결을 확인하고 다시 시도해주세요."
    case .decoding:
      return "투표 목록을 표시하지 못했습니다. 잠시 후 다시 시도해주세요."
    case .backend:
      return "투표 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
    case .unexpected:
      return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."
    }
  }
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
    } catch let error as VoteServiceError {
      createdRoomState = .failed(failure(for: error))
    } catch {
      createdRoomState = .failed(.unexpected)
    }
  }

  private func failure(for error: VoteServiceError) -> VoteListFailure {
    switch error {
    case .network:
      return .network
    case .decoding:
      return .decoding
    case .voteRoomNotFound, .participantNicknameConflict,
      .participantCredentialInvalid, .validationFailed:
      return .backend
    case .unexpected:
      return .unexpected
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
