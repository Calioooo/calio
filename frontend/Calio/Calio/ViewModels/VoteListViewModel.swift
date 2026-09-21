import Foundation

enum VoteListLoadState: Equatable {
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
}
