import Foundation
import Testing

@testable import Calio

@Suite(.serialized)
struct VoteListViewModelTests {
  @Test @MainActor func loadingMyVoteRoomsPreservesCreatedAndParticipatedContracts() async {
    let publicId = UUID(uuidString: "9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F")!
    let service = VoteService(
      repository: VoteListRepositoryStub(
        createdRooms: [
          VoteRoomResponseDTO(
            publicId: publicId,
            name: "내가 만든 투표",
            candidateStartDate: "2026-09-18",
            candidateEndDate: "2026-10-18"
          )
        ],
        participatedRooms: [
          ParticipatedVoteRoomResponseDTO(
            publicId: publicId,
            name: "참여한 투표",
            candidateStartDate: "2026-10-10",
            candidateEndDate: "2026-10-18",
            nickname: "민지",
            participantStatus: .submitted,
            participantUpdatedAt: Date(timeIntervalSince1970: 1_792_337_800)
          ),
          ParticipatedVoteRoomResponseDTO(
            publicId: publicId,
            name: "참여한 투표",
            candidateStartDate: "2026-10-10",
            candidateEndDate: "2026-10-18",
            nickname: "여행팀 민지",
            participantStatus: .registered,
            participantUpdatedAt: Date(timeIntervalSince1970: 1_792_337_700)
          ),
        ]
      )
    )
    let viewModel = VoteListViewModel(voteService: service)

    await viewModel.loadIfNeeded()

    #expect(
      viewModel.createdRoomState
        == .loaded([
          VoteRoom(
            publicId: publicId,
            name: "내가 만든 투표",
            candidateStartDay: VoteDay(year: 2026, month: 9, day: 18),
            candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
          )
        ])
    )
    #expect(
      viewModel.participatedRoomState
        == .loaded([
          ParticipatedVoteRoom(
            room: VoteRoom(
              publicId: publicId,
              name: "참여한 투표",
              candidateStartDay: VoteDay(year: 2026, month: 10, day: 10),
              candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
            ),
            nickname: "민지",
            participantStatus: .submitted,
            participantUpdatedAt: Date(timeIntervalSince1970: 1_792_337_800)
          ),
          ParticipatedVoteRoom(
            room: VoteRoom(
              publicId: publicId,
              name: "참여한 투표",
              candidateStartDay: VoteDay(year: 2026, month: 10, day: 10),
              candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
            ),
            nickname: "여행팀 민지",
            participantStatus: .registered,
            participantUpdatedAt: Date(timeIntervalSince1970: 1_792_337_700)
          ),
        ])
    )
  }

  @Test @MainActor func createdRoomFailureDoesNotHideParticipatedRooms() async {
    let service = VoteService(
      repository: VoteListRepositoryStub(
        participatedRooms: [],
        createdError: VoteServiceError.network
      )
    )
    let viewModel = VoteListViewModel(voteService: service)

    await viewModel.loadIfNeeded()

    #expect(viewModel.createdRoomState == .failed(.network))
    #expect(viewModel.participatedRoomState == .loaded([]))
  }
}

private final class VoteListRepositoryStub: VoteRepository {
  private let createdRooms: [VoteRoomResponseDTO]
  private let participatedRooms: [ParticipatedVoteRoomResponseDTO]
  private let createdError: Error?

  init(
    createdRooms: [VoteRoomResponseDTO] = [],
    participatedRooms: [ParticipatedVoteRoomResponseDTO] = [],
    createdError: Error? = nil
  ) {
    self.createdRooms = createdRooms
    self.participatedRooms = participatedRooms
    self.createdError = createdError
  }

  func createVoteRoom(_: CreateVoteRoomRequestDTO) async throws -> VoteRoomResponseDTO {
    fatalError()
  }

  func fetchMyCreatedVoteRooms() async throws -> [VoteRoomResponseDTO] {
    if let createdError { throw createdError }
    return createdRooms
  }

  func fetchMyParticipatedVoteRooms() async throws -> [ParticipatedVoteRoomResponseDTO] {
    participatedRooms
  }

  func fetchVoteResult(publicId _: UUID) async throws -> VoteResultResponseDTO { fatalError() }

  func createVoteParticipant(
    publicId _: UUID,
    request _: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO { fatalError() }

  func createAuthenticatedVoteParticipant(
    publicId _: UUID,
    request _: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO { fatalError() }

  func lookupVoteParticipantSelection(
    publicId _: UUID,
    request _: LookupVoteParticipantSelectionRequestDTO
  ) async throws -> VoteParticipantSelectionResponseDTO { fatalError() }

  func submitVotes(
    publicId _: UUID,
    request _: SubmitVoteRequestDTO
  ) async throws -> VoteSubmissionResponseDTO { fatalError() }
}
