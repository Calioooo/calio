import Foundation

struct CreateVoteRoomRequestDTO: Encodable, Equatable {
  let name: String
  let candidateEndDate: String
}

struct VoteRoomResponseDTO: Decodable {
  let publicId: UUID
  let name: String
  let candidateStartDate: String
  let candidateEndDate: String
}

struct VoteDateResultResponseDTO: Decodable {
  let date: String
  let unavailableCount: Int
  let unavailableNicknames: [String]
}

struct VoteResultResponseDTO: Decodable {
  let publicId: UUID
  let name: String
  let candidateStartDate: String
  let candidateEndDate: String
  let dates: [VoteDateResultResponseDTO]
  let submittedNicknames: [String]
}

enum VoteParticipantStatusDTO: String, Decodable {
  case registered = "REGISTERED"
  case submitted = "SUBMITTED"
}

struct CreateVoteParticipantRequestDTO: Encodable, Equatable {
  let nickname: String
  let password: String?
}

struct VoteParticipantResponseDTO: Decodable {
  let nickname: String
  let status: VoteParticipantStatusDTO
}

struct LookupVoteParticipantSelectionRequestDTO: Encodable, Equatable {
  let nickname: String
  let password: String?
}

struct VoteParticipantSelectionResponseDTO: Decodable {
  let nickname: String
  let status: VoteParticipantStatusDTO
  let unavailableDates: [String]
}

struct SubmitVoteRequestDTO: Encodable, Equatable {
  let nickname: String
  let password: String?
  let unavailableDates: [String]
}

struct VoteSubmissionResponseDTO: Decodable {
  let nickname: String
  let status: VoteParticipantStatusDTO
  let unavailableDates: [String]
}
