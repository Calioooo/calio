import Foundation

struct VoteService {
  private let repository: VoteRepository

  init(repository: VoteRepository = URLSessionVoteRepository()) {
    self.repository = repository
  }

  func createRoom(
    name: String,
    candidateStartDay: VoteDay,
    candidateEndDay: VoteDay
  ) async throws -> VoteRoom {
    let request = CreateVoteRoomRequestDTO(
      name: name,
      candidateStartDate: candidateStartDay.apiDateString,
      candidateEndDate: candidateEndDay.apiDateString
    )
    let response = try await perform { try await repository.createVoteRoom(request) }
    return try mapVoteRoom(response)
  }

  func fetchResult(publicId: UUID) async throws -> VoteResult {
    let response = try await perform { try await repository.fetchVoteResult(publicId: publicId) }
    return try mapVoteResult(response)
  }

  func createParticipant(
    publicId: UUID,
    nickname: String,
    password: String?
  ) async throws -> VoteParticipant {
    let request = CreateVoteParticipantRequestDTO(nickname: nickname, password: password)
    let response = try await perform {
      try await repository.createVoteParticipant(publicId: publicId, request: request)
    }
    return mapVoteParticipant(response)
  }

  func createAuthenticatedParticipant(
    publicId: UUID,
    nickname: String,
    password: String?
  ) async throws -> VoteParticipant {
    let request = CreateVoteParticipantRequestDTO(nickname: nickname, password: password)
    let response = try await perform {
      try await repository.createAuthenticatedVoteParticipant(publicId: publicId, request: request)
    }
    return mapVoteParticipant(response)
  }

  func lookupParticipantSelection(
    publicId: UUID,
    nickname: String,
    password: String?
  ) async throws -> VoteParticipantSelection {
    let request = LookupVoteParticipantSelectionRequestDTO(nickname: nickname, password: password)
    let response = try await perform {
      try await repository.lookupVoteParticipantSelection(publicId: publicId, request: request)
    }
    return try mapVoteParticipantSelection(response)
  }

  func submitVotes(
    publicId: UUID,
    nickname: String,
    password: String?,
    unavailableDays: [VoteDay]
  ) async throws -> VoteSubmission {
    let request = SubmitVoteRequestDTO(
      nickname: nickname,
      password: password,
      unavailableDates: unavailableDays.map(\.apiDateString)
    )
    let response = try await perform {
      try await repository.submitVotes(publicId: publicId, request: request)
    }
    return try mapVoteSubmission(response)
  }

  private func perform<T>(_ operation: () async throws -> T) async throws -> T {
    do {
      return try await operation()
    } catch is CancellationError {
      throw CancellationError()
    } catch let error as APIError {
      throw VoteServiceError(apiError: error)
    } catch let error as VoteServiceError {
      throw error
    } catch {
      throw VoteServiceError.unexpected
    }
  }

  private func mapVoteRoom(_ dto: VoteRoomResponseDTO) throws -> VoteRoom {
    VoteRoom(
      publicId: dto.publicId,
      name: dto.name,
      candidateStartDay: try voteDay(from: dto.candidateStartDate),
      candidateEndDay: try voteDay(from: dto.candidateEndDate)
    )
  }

  private func mapVoteResult(_ dto: VoteResultResponseDTO) throws -> VoteResult {
    let room = try mapVoteRoom(
      VoteRoomResponseDTO(
        publicId: dto.publicId,
        name: dto.name,
        candidateStartDate: dto.candidateStartDate,
        candidateEndDate: dto.candidateEndDate
      )
    )
    return VoteResult(
      room: room,
      dateResults: try dto.dates.map(mapVoteDateResult(_:)),
      submittedNicknames: dto.submittedNicknames
    )
  }

  private func mapVoteDateResult(_ dto: VoteDateResultResponseDTO) throws -> VoteDateResult {
    VoteDateResult(
      day: try voteDay(from: dto.date),
      unavailableCount: dto.unavailableCount,
      unavailableNicknames: dto.unavailableNicknames
    )
  }

  private func mapVoteParticipant(_ dto: VoteParticipantResponseDTO) -> VoteParticipant {
    VoteParticipant(nickname: dto.nickname, status: mapVoteParticipantStatus(dto.status))
  }

  private func mapVoteParticipantSelection(
    _ dto: VoteParticipantSelectionResponseDTO
  ) throws -> VoteParticipantSelection {
    VoteParticipantSelection(
      participant: VoteParticipant(
        nickname: dto.nickname, status: mapVoteParticipantStatus(dto.status)),
      unavailableDays: try dto.unavailableDates.map(voteDay(from:))
    )
  }

  private func mapVoteSubmission(_ dto: VoteSubmissionResponseDTO) throws -> VoteSubmission {
    VoteSubmission(
      participant: VoteParticipant(
        nickname: dto.nickname, status: mapVoteParticipantStatus(dto.status)),
      unavailableDays: try dto.unavailableDates.map(voteDay(from:))
    )
  }

  private func mapVoteParticipantStatus(_ dto: VoteParticipantStatusDTO) -> VoteParticipantStatus {
    switch dto {
    case .registered:
      return .registered
    case .submitted:
      return .submitted
    }
  }

  private func voteDay(from value: String) throws -> VoteDay {
    guard let day = VoteDay(apiDateString: value) else {
      throw VoteServiceError.decoding
    }
    return day
  }
}

enum VoteServiceError: Error, Equatable {
  case voteRoomNotFound
  case participantNicknameConflict
  case participantCredentialInvalid
  case validationFailed
  case network
  case decoding
  case unexpected

  init(apiError: APIError) {
    switch apiError {
    case .backend(_, let problem):
      switch problem?.errorCode {
      case "VOTE_ROOM_NOT_FOUND":
        self = .voteRoomNotFound
      case "VOTE_PARTICIPANT_NICKNAME_CONFLICT":
        self = .participantNicknameConflict
      case "VOTE_PARTICIPANT_CREDENTIAL_INVALID":
        self = .participantCredentialInvalid
      case "VALIDATION_FAILED":
        self = .validationFailed
      default:
        self = .unexpected
      }
    case .network:
      self = .network
    case .decoding:
      self = .decoding
    case .invalidRequest, .invalidResponse, .encoding, .unexpected:
      self = .unexpected
    }
  }
}
