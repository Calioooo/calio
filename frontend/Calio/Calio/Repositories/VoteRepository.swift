import Foundation

protocol VoteRepository {
  func createVoteRoom(_ request: CreateVoteRoomRequestDTO) async throws -> VoteRoomResponseDTO
  func fetchMyParticipatedVoteRooms() async throws -> [ParticipatedVoteRoomResponseDTO]
  func fetchVoteResult(publicId: UUID) async throws -> VoteResultResponseDTO
  func createVoteParticipant(
    publicId: UUID,
    request: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO
  func createAuthenticatedVoteParticipant(
    publicId: UUID,
    request: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO
  func lookupVoteParticipantSelection(
    publicId: UUID,
    request: LookupVoteParticipantSelectionRequestDTO
  ) async throws -> VoteParticipantSelectionResponseDTO
  func submitVotes(
    publicId: UUID,
    request: SubmitVoteRequestDTO
  ) async throws -> VoteSubmissionResponseDTO
}
