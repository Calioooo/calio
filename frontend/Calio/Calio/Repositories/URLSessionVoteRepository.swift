import Foundation

struct URLSessionVoteRepository: VoteRepository {
  private let apiClient: APIClient

  init(
    baseURL: URL = CalioAPIConfig.baseURL,
    session: URLSession = .shared,
    jsonDecoder: JSONDecoder = APIJSONCoding.makeDecoder(),
    jsonEncoder: JSONEncoder = APIJSONCoding.makeEncoder(),
    authTokenProvider: AuthTokenProvider? = KeychainAuthTokenStore.shared
  ) {
    apiClient = APIClient(
      baseURL: baseURL,
      session: session,
      jsonDecoder: jsonDecoder,
      jsonEncoder: jsonEncoder,
      authTokenProvider: authTokenProvider
    )
  }

  func createVoteRoom(_ request: CreateVoteRoomRequestDTO) async throws -> VoteRoomResponseDTO {
    try await apiClient.send(
      VoteRoomResponseDTO.self,
      method: .post,
      pathComponents: ["api", "vote-rooms"],
      authorization: .bearer,
      body: request
    )
  }

  func fetchMyParticipatedVoteRooms() async throws -> [ParticipatedVoteRoomResponseDTO] {
    try await apiClient.send(
      [ParticipatedVoteRoomResponseDTO].self,
      method: .get,
      pathComponents: ["api", "vote-rooms", "me", "participated"],
      authorization: .bearer
    )
  }

  func fetchVoteResult(publicId: UUID) async throws -> VoteResultResponseDTO {
    try await apiClient.send(
      VoteResultResponseDTO.self,
      method: .get,
      pathComponents: voteRoomPath(publicId),
      authorization: .none
    )
  }

  func createVoteParticipant(
    publicId: UUID,
    request: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO {
    try await apiClient.send(
      VoteParticipantResponseDTO.self,
      method: .post,
      pathComponents: voteRoomPath(publicId) + ["participants"],
      authorization: .none,
      body: request
    )
  }

  func createAuthenticatedVoteParticipant(
    publicId: UUID,
    request: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO {
    try await apiClient.send(
      VoteParticipantResponseDTO.self,
      method: .post,
      pathComponents: voteRoomPath(publicId) + ["participants", "me"],
      authorization: .bearer,
      body: request
    )
  }

  func lookupVoteParticipantSelection(
    publicId: UUID,
    request: LookupVoteParticipantSelectionRequestDTO
  ) async throws -> VoteParticipantSelectionResponseDTO {
    try await apiClient.send(
      VoteParticipantSelectionResponseDTO.self,
      method: .post,
      pathComponents: voteRoomPath(publicId) + ["votes", "lookup"],
      authorization: .none,
      body: request
    )
  }

  func submitVotes(
    publicId: UUID,
    request: SubmitVoteRequestDTO
  ) async throws -> VoteSubmissionResponseDTO {
    try await apiClient.send(
      VoteSubmissionResponseDTO.self,
      method: .put,
      pathComponents: voteRoomPath(publicId) + ["votes"],
      authorization: .none,
      body: request
    )
  }

  private func voteRoomPath(_ publicId: UUID) -> [String] {
    ["api", "vote-rooms", publicId.uuidString]
  }
}
